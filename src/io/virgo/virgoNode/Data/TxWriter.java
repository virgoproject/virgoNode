package io.virgo.virgoNode.Data;

import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.Transaction;

public class TxWriter implements Runnable {

	LinkedBlockingQueue<Transaction> queue = new LinkedBlockingQueue<Transaction>();
	DAG dag;
	
	public TxWriter(DAG dag) {
		this.dag = dag;
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			@Override
			public void run() {
				saveTips();
			}
			
		}));
		
		Timer timer = new Timer();
		
		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				saveTips();
			}
			
		}, 10000l, dag.saveInterval);
	}
	
	private void saveTips() {
		try {
			
			Main.getDatabase().setTips(dag.getTips());
			
		} catch (SQLException e) {
			System.out.println("Unable to save Tips: " + e.getMessage());
		}
	}
	
	public void push(Transaction tx) {
		if(tx == null) return;
		
		if(!queue.contains(tx))
			queue.add(tx);
	}
	
	@Override
	public void run() {
		
		while(true) {
			
			try {
				Transaction tx = queue.take();
				System.out.println("saving " + tx.getUid());
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
