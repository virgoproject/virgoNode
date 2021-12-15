package io.virgo.virgoNode.DAG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.virgo.virgoNode.Main;

public class Pruner implements Runnable {

	private int minElements = 10000;//minimal number of elements to keep in memory
	private float startThresold = 0.7f;//memory usage percentile at which pruner will execute
	private float targetUsage = 0.4f;
	private float currentUsage = 0f;
	
	public ArrayList<LoadedTransaction> loadedTransactions = new ArrayList<LoadedTransaction>();
	public LinkedBlockingQueue<LoadedTransaction> queue = new LinkedBlockingQueue<LoadedTransaction>();
	
	public long nextCheck = System.currentTimeMillis();
	
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
			
			try {
				loadedTransactions.add(queue.take());
			} catch (InterruptedException e) {}
			
			if(System.currentTimeMillis() > nextCheck) {
				currentUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/(Runtime.getRuntime().maxMemory()*1.0f);
				if(currentUsage > startThresold && loadedTransactions.size() > minElements)
					prune();
				else
					nextCheck = System.currentTimeMillis() + 10000;
			}

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
		
		ArrayList<LoadedTransaction> txsToPrune = new ArrayList<LoadedTransaction>();

		
		int toRemove = Math.min(loadedTransactions.size()-10000, (int) (loadedTransactions.size()*currentUsage-targetUsage));
		
		Collections.sort(loadedTransactions, comparator);
		
		for(int i = 0; i < toRemove; i++) {
			LoadedTransaction tx = loadedTransactions.get(i);
			try {
				if(tx.baseTransaction.lock.writeLock().tryLock(1, TimeUnit.SECONDS))
					txsToPrune.add(tx);
			}catch(Exception e) {}
		}
		
		try {
			if(Main.getDatabase().insertStates(txsToPrune))
				for(LoadedTransaction tx : txsToPrune) {
					tx.baseTransaction.loadedTx = null;
					loadedTransactions.remove(tx);
				}
		} catch (SQLException e) {}
		
		for(LoadedTransaction tx : txsToPrune)
			tx.releaseWriteLock();
		
		System.gc();
		
		nextCheck = System.currentTimeMillis() + 60000;
		
	}

}
