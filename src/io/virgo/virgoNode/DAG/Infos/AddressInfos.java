package io.virgo.virgoNode.DAG.Infos;

import java.util.ArrayList;

import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;
import io.virgo.virgoNode.DAG.TxStatus;

public class AddressInfos {

	private String address;
	private ArrayList<String> inputs = new ArrayList<String>();
	private ArrayList<String> outputs = new ArrayList<String>();

	private ArrayList<String> unspentInputs = new ArrayList<String>();
	
	private long totalReceived = 0;
	private long totalSent = 0;
	
	public AddressInfos(String address) {
		this.address = address;
	}
	
	/**
	 * Add a tx to the resume if it has something to do with this address
	 * 
	 * @param tx the tx to add
	 */
	public void addTx(LoadedTransaction tx) {
		
		if(inputs.contains(tx.getUid()) || outputs.contains(tx.getUid()))
			return;

		//calculate this transaction's impact on the address balance
		
		long total = 0;
		
		if(tx.getAddress().equals(getAddress()))//we are sending funds
			//get every input transaction and substract it's value from total
			total -= tx.getTotalInput();
		
		//get the return output and add it to total, also add tx to inputs because there is something to spend on it
		TxOutput input = tx.getOutputsMap().get(getAddress());
		if(input != null){
			total += input.getAmount();
			inputs.add(tx.getUid());
			if(!tx.getStatus().isConfirmed())
				unspentInputs.add(tx.getUid());
		}else outputs.add(tx.getUid());
		
		
		//this transaction had something to do with this address
		if(total > 0)
			//input transaction
			if(tx.getStatus().isConfirmed())
				totalReceived += total;
		else if(total < 0)
			if(tx.getStatus().isConfirmed())
				totalSent += Math.abs(total);
		
		
	}
	
	public void updateTx(LoadedTransaction tx, TxStatus newStatus, TxStatus formerStatus) {
		if(tx == null)
			return;
		
		long total = 0;
		
		if(tx.getAddress().equals(getAddress()))//we are sending funds
			//get every input transaction and substract it's value from total
			total -= tx.getTotalInput();
		
		//get the return output and add it to total, also add tx to inputs because there is something to spend on it
		TxOutput input = tx.getOutputsMap().get(getAddress());
		if(input != null) {
			total += input.getAmount();	
			if(!inputs.contains(tx.getUid()))
				inputs.add(tx.getUid());
			
			if(newStatus.isConfirmed())
				unspentInputs.remove(tx.getUid());
			else if(!unspentInputs.contains(tx.getUid()))
				unspentInputs.add(tx.getUid());
			
		}else if(!outputs.contains(tx.getUid()))
			outputs.add(tx.getUid());
			

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

	public ArrayList<String> getInputs() {
		return new ArrayList<String>(inputs);
	}
	
	public ArrayList<String> getUnspentInputs() {
		return new ArrayList<String>(inputs);
	}
	
	public ArrayList<String> getOutputs() {
		return new ArrayList<String>(outputs);
	}
	
}
