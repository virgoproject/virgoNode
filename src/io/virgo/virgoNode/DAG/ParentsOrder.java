package io.virgo.virgoNode.DAG;

public enum ParentsOrder {

	FIRST_YOUNGER(1),
	SECOND_YOUNGER(2),
	NO_ORDER(0);
	
	private int order;
	
	ParentsOrder(int order){
		this.order = order;
	}
	
	public boolean isFirstYounger() {
		return this == FIRST_YOUNGER;
	}
	
	public boolean isSecondYounger() {
		return this == SECOND_YOUNGER;
	}
	
	public boolean hasNoOrder() {
		return this == NO_ORDER;
	}	
	
	public int getCardinalOrder() {
		return order;
	}
}
