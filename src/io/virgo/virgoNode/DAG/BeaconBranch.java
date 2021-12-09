package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.virgo.virgoNode.Main;

/**
 * DAG sub data-structure simplifying beaconchain graph into branches
 * for simplier calculation of beacons weight
 */
public class BeaconBranch {
	
	private String uuid;
	
	private ArrayList<Transaction> transactions = new ArrayList<Transaction>();
	private BigInteger branchWeight = BigInteger.ZERO;
	
	public BeaconBranch() {
		uuid = UUID.randomUUID().toString();
		Main.getDAG().branches.put(uuid, this);
	}
	
	/**
	 * Add a beacon to this branch and return it's displacement
	 * Displacement is this branch weight at time of addition
	 * branch weight is sum of all beacons weights
	 * To get a precise beacon weight in this branch just do branchWeight-beaconDisplacement
	 * 
	 */
	public BigInteger addTx(LoadedTransaction tx) {
		
		transactions.add(tx.baseTransaction);
		
		BigInteger displacement = branchWeight;
		
		branchWeight = branchWeight.add(tx.getDifficulty());
		
		return displacement;
		
	}
	
	public BigInteger getBranchWeight() {
		return branchWeight;
	}
	
	public int indexOf(Transaction transaction) {
		return transactions.indexOf(transaction);
	}
	
	public long getBranchConfirmations() {
		return transactions.size();
	}

	/**
	 * Get a list of transactions added before a specified transaction, including the given transaction
	 */
	public List<Transaction> getMembersBefore(Transaction loadedParentBeacon) {
		return transactions.subList(0, transactions.indexOf(loadedParentBeacon)+1);
	}
	
	public Transaction getFirst() {
		return transactions.get(0);
	}

	public String getUUID() {
		return uuid;
	}
	
}
