package io.virgo.virgoNode.Data;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.Transaction;

/**
 * Runnable writing transactions to disk
 */
public class TxWriter implements Runnable {

	LinkedBlockingQueue<LoadedTransaction> queue = new LinkedBlockingQueue<LoadedTransaction>();
	DAG dag;
	
	public TxWriter(DAG dag) {
		this.dag = dag;	
	}

	public void push(LoadedTransaction tx) {		
		if(!queue.contains(tx))
			queue.add(tx);
	}
	
	@Override
	public void run() {
		
		while(true) {
			
			try {
				LoadedTransaction tx = queue.take();
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
