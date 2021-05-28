package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BeaconBranch {
	
	private ArrayList<LoadedTransaction> transactions = new ArrayList<LoadedTransaction>();
	private BigInteger branchWeight = BigInteger.ZERO;
	
	public BeaconBranch() {}
	
	public BigInteger addTx(LoadedTransaction tx) {
		
		transactions.add(tx);
		
		BigInteger displacement = branchWeight;
		
		branchWeight = branchWeight.add(tx.getDifficulty());
		
		return displacement;
		
	}
	
	public BigInteger getBranchWeight() {
		return branchWeight;
	}
	
	public int indexOf(LoadedTransaction transaction) {
		return transactions.indexOf(transaction);
	}
	
	public long getBranchConfirmations() {
		return transactions.size();
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
	
}
