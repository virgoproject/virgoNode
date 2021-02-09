package io.virgo.virgoNode.DAG;

public class WeightModifier {

	public enum Modifier {
		NONE,
		DIV,
		SUB;
	}
	
	private Modifier modifier;
	private long value;
	
	public WeightModifier(Modifier modifier, long value) {
		
		this.modifier = modifier;
		this.value = value;
		
	}
	
	public long apply(long value) {
		switch(modifier) {
		case NONE:
			return value;
		case DIV:
			return value/this.value;
		case SUB:
			return value-this.value;
		default:
			return value;
		}
	}
	
	public WeightModifier.Modifier getModifier(){
		return modifier;
	}
	
	public long getValue() {
		return value;
	}
	
	public void setValue(long value) {
		this.value = value;
	}
	
}