package io.virgo.virgoNode.DAG;

import java.util.Collections;
import java.util.Comparator;

import io.virgo.virgoNode.Main;

public class Pruner implements Runnable {

	private int minElements = 10000;//minimal number of elements to keep in memory
	private float startThresold = 0.7f;//memory usage percentile at which pruner will execute
	private float targetUsage = 0.5f;
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
			
			currentUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/Runtime.getRuntime().maxMemory();
			
			if(currentUsage > startThresold && Main.getDAG().loadedTransactions.size() > minElements)
				prune();
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}

	private void prune() {
		int toRemove = Math.min(Main.getDAG().loadedTransactions.size()-10000, (int) (Main.getDAG().loadedTransactions.size()*currentUsage-targetUsage));
		
		Collections.sort(Main.getDAG().loadedTransactions, comparator);
		
		for(int i = 0; i < toRemove; i++)
			Main.getDAG().loadedTransactions.get(i).baseTransaction.unload();
		
		System.gc();
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {}
		
	}

}
