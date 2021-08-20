package io.virgo.virgoNode.DAG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class Pruner implements Runnable {

	private int minElements = 10000;//minimal number of elements to keep in memory
	private float startThresold = 0.1f;//memory usage percentile at which pruner will execute
	private float targetUsage = 0.05f;
	private float currentUsage = 0f;
	
	private Comparator<LoadedTransaction> comparator = new Comparator<LoadedTransaction>() {

		@Override
		public int compare(LoadedTransaction arg0, LoadedTransaction arg1) {
			return (int) (arg0.lastAccessed - arg1.lastAccessed);
		}
		
	};
	
	public Pruner() {
	}
	
	@Override
	public void run() {
		while(!Thread.interrupted()) {
			
			currentUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/(Runtime.getRuntime().maxMemory()*1.0f);
			if(currentUsage > startThresold && Main.getDAG().loadedTransactions.size() > minElements)
				prune();
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}

	/**
	 * Sort LoadedTransactions by last access date
	 * Acquire lock on thoses we want to remove
	 * Write states to database
	 * If successfull unload LoadedTransaction objects
	 * Finally release locks
	 */
	private void prune() {
		System.out.println("pruner running");
		
		int toRemove = Math.min(Main.getDAG().loadedTransactions.size()-10000, (int) (Main.getDAG().loadedTransactions.size()*currentUsage-targetUsage));
		
		Collections.sort(Main.getDAG().loadedTransactions, comparator);
		
		ArrayList<LoadedTransaction> txsToPrune = new ArrayList<LoadedTransaction>();
		
		for(int i = 0; i < toRemove; i++) {
			LoadedTransaction tx = Main.getDAG().loadedTransactions.get(i);
			try {
				if(tx.baseTransaction.lock.writeLock().tryLock(1, TimeUnit.SECONDS))
					txsToPrune.add(tx);
			}catch(Exception e) {}
		}
		
		try {
			if(Main.getDatabase().insertStates(txsToPrune))
				for(LoadedTransaction tx : txsToPrune) {
					tx.baseTransaction.loadedTx = null;
					Main.getDAG().loadedTransactions.remove(tx);
				}
		} catch (SQLException e) {}
		
		for(LoadedTransaction tx : txsToPrune)
			tx.releaseWriteLock();
		
		System.gc();
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {}
		
	}

}
