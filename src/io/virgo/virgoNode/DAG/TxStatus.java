package io.virgo.virgoNode.DAG;

public enum TxStatus {
	
	PENDING(0),
	CONFIRMED(1),
	REFUSED(2);
	
	private int code;
	
	TxStatus(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
	
	public boolean isPending() {
		return this == PENDING;
	}
	
	public boolean isConfirmed() {
		return this == CONFIRMED;
	}
	
	public boolean isRefused() {
		return this == REFUSED;
	}
	
	public static TxStatus fromCode(int code) {
		switch(code) {
		case 0:
			return PENDING;
		case 1:
			return CONFIRMED;
		case 2:
			return REFUSED;
			default:
				return PENDING;
		}
	}
}
