package io.virgo.virgoNode.DAG.Infos;

import java.util.ArrayList;

import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxStatus;

public class AddressInfos {

	private String address;
	private ArrayList<String> inputTxs = new ArrayList<String>();
	private ArrayList<String> outputTxs = new ArrayList<String>();
	
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
		
		if(tx.getAddress().equals(address) && !outputTxs.contains(tx.getUid())) {//sent
			
			outputTxs.add(tx.getUid());
			
			if(tx.getStatus().isConfirmed())
				totalSent += tx.getOutputsValue() - tx.getReturnAmount();
			
		}else if(tx.getOutputsMap().containsKey(address) && !inputTxs.contains(tx.getUid())) {//received
			
			inputTxs.add(tx.getUid());
			
			if(tx.getStatus().isConfirmed())
				totalReceived += tx.getOutputsMap().get(address).getAmount();
			
		}
		
	}
	
	public void updateTx(LoadedTransaction tx, TxStatus newStatus, TxStatus formerStatus) {
		if(tx == null)
			return;
		
		if(inputTxs.contains(tx.getUid())) {//received
			if(formerStatus.isPending()) {
				if(newStatus.isConfirmed()) {
					totalReceived += tx.getOutputsMap().get(address).getAmount();
				}
			} else {
				if(newStatus.isConfirmed()) {
					totalReceived += tx.getOutputsMap().get(address).getAmount();
				} else {
					totalReceived -= tx.getOutputsMap().get(address).getAmount();
				}
			}
		}
		
		if(outputTxs.contains(tx.getUid())) {//sent
			if(formerStatus.isPending()) {
				if(newStatus.isConfirmed())
					totalSent += tx.getOutputsValue() - tx.getReturnAmount();
				
			} else {
				if(newStatus.isConfirmed())
					totalSent += tx.getOutputsValue() - tx.getReturnAmount();
				else
					totalSent -= tx.getOutputsValue() - tx.getReturnAmount();
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

	public String[] getInputTxs() {
		return inputTxs.toArray(new String[inputTxs.size()]);
	}
	
	public String[] getOutputTxs() {
		return outputTxs.toArray(new String[outputTxs.size()]);
	}
	
}
