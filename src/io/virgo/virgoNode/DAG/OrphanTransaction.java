package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.Arrays;

import io.virgo.virgoNode.Main;

/**
 * Transaction waiting for parents or inputs to be loaded
 */
public class OrphanTransaction extends Transaction {
	
	private ArrayList<String> waitedTxs;

	public OrphanTransaction(Transaction tx, String[] waitedTxs) {
		super(tx);
		
		this.waitedTxs = new ArrayList<String>(Arrays.asList(waitedTxs));
	}

	/**
	 * Called when a transaction we where waiting for is loaded
	 */
	public void removeWaitedTx(String tx) {
		waitedTxs.remove(tx);
		
		//if no more transaction to wait load this transaction to DAG
		if(waitedTxs.size() == 0) {
			Main.getDAG().waitingTxsUids.remove(getUid());
			if(isBeaconTransaction())
				Main.getDAG().initBeaconTx(this);
			else
				Main.getDAG().initTx(this);
		}
	}
}
