package io.virgo.virgoNode.DAG.Events;

import io.virgo.virgoNode.DAG.LoadedTransaction;

public abstract class Event {

	final EventType type;
	final LoadedTransaction affectedTransaction;
	
	public Event(EventType type, LoadedTransaction affectedTransaction) {
		this.type = type;
		this.affectedTransaction = affectedTransaction;
	}
	
}
