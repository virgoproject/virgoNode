package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.Arrays;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

/**
 * Transaction waiting for parents or inputs to be loaded
 */
public class OrphanTransaction extends Transaction {
	
	private ArrayList<Sha256Hash> waitedTxs;

	public OrphanTransaction(Transaction tx, Sha256Hash[] waitedTxs) {
		super(tx);
		
		this.waitedTxs = new ArrayList<Sha256Hash>(Arrays.asList(waitedTxs));
	}

	/**
	 * Called when a transaction we where waiting for is loaded
	 */
	public void removeWaitedTx(Sha256Hash tx) {
		waitedTxs.remove(tx);
		
		//if no more transaction to wait load this transaction to DAG
		if(waitedTxs.size() == 0) {
			Main.getDAG().waitingTxsUids.remove(getUid());
			Main.getDAG().transactionExecutorPool.submit(new AddTransactionTask(this));
		}
	}
}
