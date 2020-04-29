package io.virgo.virgoNode.DAG.Events;

import java.util.concurrent.LinkedBlockingQueue;

import io.virgo.virgoNode.DAG.DAG;

public class EventListener implements Runnable {
	
	DAG dag;
	private boolean interrupt = false;
	
	private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	
	public EventListener(DAG dag) {
		this.dag = dag;
	}
	
	
	public void notify(Event event) {
		queue.add(event);
	}
	
	
	public void onTransactionLoaded(TransactionLoadedEvent e) {
		try {
			dag.infos.getAddressInfos(e.affectedTransaction.getAddress()).addTx(e.affectedTransaction);
		}catch(IllegalArgumentException exc) { }
		
		e.affectedTransaction.getOutputsMap().forEach((k,v) -> dag.infos.getAddressInfos(k).addTx(e.affectedTransaction));
	}
	
	public void onTransactionStatusChanged(TransactionStatusChangedEvent e) {
		dag.infos.getAddressInfos(e.affectedTransaction.getAddress()).updateTx(e.affectedTransaction, e.newStatus, e.formerStatus);
		e.affectedTransaction.getOutputsMap().forEach((k,v) -> dag.infos.getAddressInfos(k).updateTx(e.affectedTransaction, e.newStatus, e.formerStatus));
	}


	@Override
	public void run() {
		while(!interrupt) {
			try {
				Event event = queue.take();
				switch (event.type) {
					case TRANSACTION_LOADED:
						onTransactionLoaded((TransactionLoadedEvent) event);
						break;
					case TRANSACTION_STATUS_CHANGED:
						onTransactionStatusChanged((TransactionStatusChangedEvent) event);
						break;
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void interrupt() {
		interrupt = true;
	}
	
}
