package io.virgo.virgoNode.DAG;

import io.virgo.virgoNode.Main;

public class AddTransactionTask implements Runnable {

	private Transaction transaction;
	
	public AddTransactionTask(Transaction transaction) {
		this.transaction = transaction;
	}
	
	@Override
	public void run() {
		if(transaction.isBeaconTransaction())
			Main.getDAG().initBeaconTx(transaction);
		else
			Main.getDAG().initTx(transaction);
	}
	
}
