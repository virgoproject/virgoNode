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
	
	private ArrayList<LoadedTransaction> transactions = new ArrayList<LoadedTransaction>();
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

	/**
	 * Get a list of transactions added before a specified transaction, including the given transaction
	 */
	public List<LoadedTransaction> getMembersBefore(LoadedTransaction transaction) {
		return transactions.subList(0, transactions.indexOf(transaction)+1);
	}
	
	public LoadedTransaction getFirst() {
		return transactions.get(0);
	}

	public String getUUID() {
		return uuid;
	}
	
}
