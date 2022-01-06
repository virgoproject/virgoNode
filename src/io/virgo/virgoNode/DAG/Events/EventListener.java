package io.virgo.virgoNode.DAG.Events;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.TxOutput;

/**
 * Runnable handling events occurring on the node
 */
public class EventListener implements Runnable {
	
	DAG dag;
	private boolean interrupt = false;
	
	private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	
	public EventListener(DAG dag) {
		this.dag = dag;
	}
	
	
	/**
	 * Add an event to the queue
	 */
	public void notify(Event event) {
		queue.add(event);
	}
	
	/**
	 * Transaction Loaded event handler
	 * Call addTx(transaction) on the concerned AddressInfos objects
	 */
	public void onTransactionLoaded(TransactionLoadedEvent e) {
		try {
			dag.infos.getAddressInfos(e.affectedTransaction.getAddress()).addTx(e.affectedTransaction);
		}catch(IllegalArgumentException exc) { }
		
		Map<String, TxOutput> outputs = e.affectedTransaction.getOutputsMap();
		outputs.remove(e.affectedTransaction.getAddress());//remove return output from map as it has already been processed just before if it exists
		
		outputs.forEach((k,v) -> dag.infos.getAddressInfos(k).addTx(e.affectedTransaction));
		
		dag.infos.addTransaction(e.affectedTransaction);
	}
	
	/**
	 * Transaction status changed event handler
	 * Call updateTx(transaction) on the concerned AddressInfos objects
	 */
	public void onTransactionStatusChanged(TransactionStatusChangedEvent e) {
		dag.infos.getAddressInfos(e.affectedTransaction.getAddress()).updateTx(e.affectedTransaction, e.newStatus, e.formerStatus);
		
		Map<String, TxOutput> outputs = e.affectedTransaction.getOutputsMap();
		outputs.remove(e.affectedTransaction.getAddress());//remove return output from map as it has already been processed just before if it exists
		
		outputs.forEach((k,v) -> dag.infos.getAddressInfos(k).updateTx(e.affectedTransaction, e.newStatus, e.formerStatus));
	}


	/**
	 * Wait for an Event object to appear in the queue, take it and proccess it
	 * with the corresponding Event Handler
	 */
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
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void interrupt() {
		interrupt = true;
	}
	
}
