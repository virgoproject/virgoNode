package io.virgo.virgoNode.DAG.Events;

import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxStatus;

public class TransactionStatusChangedEvent extends Event {

	final TxStatus formerStatus;
	final TxStatus newStatus;
	
	public TransactionStatusChangedEvent(LoadedTransaction affectedTransaction, TxStatus formerStatus) {
		super(EventType.TRANSACTION_STATUS_CHANGED, affectedTransaction);
		
		this.formerStatus = formerStatus;
		newStatus = affectedTransaction.getStatus();
	}

}
