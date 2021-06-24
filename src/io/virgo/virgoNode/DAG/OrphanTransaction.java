package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.Arrays;

import io.virgo.virgoCryptoLib.Sha256Hash;

/**
 * Transaction waiting for parents or inputs to be loaded
 */
public class OrphanTransaction extends Transaction {
	
	public ArrayList<Sha256Hash> waitedTxs;

	public OrphanTransaction(Transaction tx, Sha256Hash[] waitedTxs) {
		super(tx);
		
		this.waitedTxs = new ArrayList<Sha256Hash>(Arrays.asList(waitedTxs));
	}

	/**
	 * Called when a transaction we were waiting for is loaded
	 * Remove waited transaction from the list of waited
	 * If no more transaction waited then try to load 
	 */
	protected void removeWaitedTx(Sha256Hash tx, DAG dag) {
		waitedTxs.remove(tx);
		
		//if no more transaction to wait load this transaction to DAG
		if(waitedTxs.size() == 0) {
			dag.waitingTxsHashes.remove(getHash());
			dag.queue.add(dag.new txTask(this));
		}
	}
}
