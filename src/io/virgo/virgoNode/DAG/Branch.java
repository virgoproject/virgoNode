package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.List;

public class Branch {

	private ArrayList<LoadedTransaction> transactions = new ArrayList<LoadedTransaction>();
	private long branchWeight = 0;

	public Branch() {
		
	}
	
	public long addTx(LoadedTransaction tx) {
		
		transactions.add(tx);
		
		long displacement = branchWeight;
		
		branchWeight+=tx.getSelfWeight();
		tx.partOf.put(this, transactions.size());
		
		return displacement;
		
	}
	
	public long getBranchWeight() {
		return branchWeight;
	}

	public List<LoadedTransaction> getMembersBefore(LoadedTransaction transaction) {
		return transactions.subList(0, transactions.indexOf(transaction)+1);
	}

	public List<LoadedTransaction> getMembersAfter(LoadedTransaction transaction) {
		return transactions.subList(transactions.indexOf(transaction)+1, transactions.size());
	}
	
	public LoadedTransaction getFirst() {
		return transactions.get(0);
	}

	public void suppressWeight(LoadedTransaction transaction) {
		branchWeight -= transaction.getSelfWeight();
		
		for(LoadedTransaction tx : getMembersAfter(transaction)) {
			WeightModifier modifier = tx.branchs.get(this);
			modifier.setValue(modifier.getValue()-transaction.getSelfWeight());
		}
	}
	
	public void addWeight(LoadedTransaction transaction) {
		branchWeight += transaction.getSelfWeight();
		
		for(LoadedTransaction tx : getMembersAfter(transaction)) {
			WeightModifier modifier = tx.branchs.get(this);
			modifier.setValue(modifier.getValue()+transaction.getSelfWeight());
		}
	}
	
}
