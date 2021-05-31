package io.virgo.virgoNode.Data;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.Transaction;

/**
 * Runnable writing transactions to disk
 */
public class TxWriter implements Runnable {

	LinkedBlockingQueue<Transaction> queue = new LinkedBlockingQueue<Transaction>();
	DAG dag;
	
	public TxWriter(DAG dag) {
		this.dag = dag;	
	}

	public void push(Transaction tx) {		
		if(!queue.contains(tx))
			queue.add(tx);
	}
	
	@Override
	public void run() {
		
		while(true) {
			
			try {
				Transaction tx = queue.take();
				try {
					Main.getDatabase().insertTx(tx);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}catch(InterruptedException e) {
				
			}
			
		}
		
	}

}
