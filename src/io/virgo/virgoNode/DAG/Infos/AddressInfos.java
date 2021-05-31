package io.virgo.virgoNode.DAG.Infos;

import java.util.ArrayList;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;
import io.virgo.virgoNode.DAG.TxStatus;

public class AddressInfos {

	private String address;
	private ArrayList<Sha256Hash> inputs = new ArrayList<Sha256Hash>();
	private ArrayList<Sha256Hash> outputs = new ArrayList<Sha256Hash>();
	private ArrayList<Sha256Hash> transactions = new ArrayList<Sha256Hash>();
	
	private long totalReceived = 0;
	private long totalSent = 0;
	
	public AddressInfos(String address) {
		this.address = address;
	}
	
	/**
	 * Add a tx to the resume if it has something to do with this address
	 * Calculate it's impact on this address
	 * 
	 * @param tx the tx to add
	 */
	public void addTx(LoadedTransaction tx) {
		
		if(transactions.contains(tx.getHash()))
			return;

		//calculate this transaction's impact on the address balance
		long total = 0;
		
		if(tx.getAddress().equals(getAddress())){
			total -= tx.getTotalInput();
			outputs.add(0, tx.getHash());
		}
		
		//get the return output and add it to total, also add tx to inputs because there is something to spend on it
		TxOutput input = tx.getOutputsMap().get(getAddress());
		if(input != null){
			total += input.getAmount();
			inputs.add(0, tx.getHash());
		}
		
		if(total != 0)
			transactions.add(0, tx.getHash());
		
		//this transaction had something to do with this address
		if(total > 0)
			//input transaction
			if(tx.getStatus().isConfirmed())
				totalReceived += total;
		else if(total < 0)
			if(tx.getStatus().isConfirmed())
				totalSent += Math.abs(total);
		
		
	}
	
	/**
	 * Update a previously processed transaction according to it's new status
	 */
	public void updateTx(LoadedTransaction tx, TxStatus newStatus, TxStatus formerStatus) {
		if(!transactions.contains(tx.getHash()))
			return;
		
		long total = 0;
		
		if(tx.getAddress().equals(getAddress()))//we are sending funds
			//get every input transaction and substract it's value from total
			total -= tx.getTotalInput();
		
		//get the return output and add it to total, also add tx to inputs because there is something to spend on it
		TxOutput input = tx.getOutputsMap().get(getAddress());
		if(input != null)
			total += input.getAmount();				
		

		if(total > 0) {
			//input transaction
			if(formerStatus.isPending())
				if(newStatus.isConfirmed())
					totalReceived += total;
			 else {
				if(newStatus.isConfirmed()) {
					totalReceived += total;
				} else {
					totalReceived -= total;
				}
			}
				
		}else if(total < 0){
			
			//output transaction
			total = Math.abs(total);
			
			if(formerStatus.isPending()) 
				if(newStatus.isConfirmed())
					totalSent += total;
				
			else {
				if(newStatus.isConfirmed())
					totalSent += total;
				else
					totalSent -= total;
			}		
				
		}
		
	}
	
	public String getAddress() {
		return address;
	}
	
	public long getTotalReceived() {
		return totalReceived;
	}
	
	public long getTotalSent() {
		return totalSent;
	}

	public ArrayList<Sha256Hash> getTransactions() {
		return new ArrayList<Sha256Hash>(transactions);
	}
	
	public ArrayList<Sha256Hash> getInputs() {
		return new ArrayList<Sha256Hash>(inputs);
	}
	
	public ArrayList<Sha256Hash> getOutputs() {
		return new ArrayList<Sha256Hash>(outputs);
	}
	
}
