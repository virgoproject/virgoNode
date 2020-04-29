package io.virgo.virgoNode.DAG.Events;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

public class TransactionLoadedEvent extends Event {

	public TransactionLoadedEvent(LoadedTransaction affectedTransaction) {
		super(EventType.TRANSACTION_LOADED, affectedTransaction);
		Main.txsSec++;
	}

}
