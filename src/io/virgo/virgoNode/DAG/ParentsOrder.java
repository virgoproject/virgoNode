package io.virgo.virgoNode.DAG;

public enum ParentsOrder {

	ORDERED(1),
	NO_ORDER(0);
	
	private int order;
	
	ParentsOrder(int order){
		this.order = order;
	}
	
	public boolean isOrdered() {
		return this == ORDERED;
	}
	
	public boolean hasNoOrder() {
		return this == NO_ORDER;
	}	
	
	public int getCardinalOrder() {
		return order;
	}
}
