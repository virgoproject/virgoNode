package io.virgo.virgoNode.DAG.Infos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.Utils.Miscellaneous;

public class DAGInfos {

	final private ConcurrentHashMap<String, AddressInfos> addresses = new ConcurrentHashMap<String, AddressInfos>(); 
	
	private ArrayList<Sha256Hash> latestTransactions = new ArrayList<Sha256Hash>();
	
	protected long transactionCount = 0;
	
	/**
	 * Get an address infos or create it if don't exist
	 * 
	 * @param address The address you want 
	 * @return An AddressInfos object resuming all the data in relation to the given address
	 */
	public AddressInfos getAddressInfos(String address) {
		if(!Miscellaneous.validateAddress(address, Main.ADDR_IDENTIFIER))
			throw new IllegalArgumentException("Invalid address: " + address);
			
		AddressInfos addrInfos = addresses.get(address);
		if(addrInfos == null) {
			addrInfos = new AddressInfos(address);
			addresses.put(address, addrInfos);
		}
			
		return addrInfos;
	}
	
	/**
	 * Called when a new transaction is loaded
	 * Currently just maintain a list of the 100000 latest loaded transactions
	 */
	public void addTransaction(LoadedTransaction transaction) {
		if(latestTransactions.size() >= 100000)
			latestTransactions.remove(0);
		
		latestTransactions.add(transaction.getHash());
		
	}
	
	public ArrayList<Sha256Hash> getLatestTransactions(int wanted){
		
		ArrayList<Sha256Hash> lastTxs = new ArrayList<Sha256Hash>(
				latestTransactions.subList(latestTransactions.size()-Math.max(1, Math.min(latestTransactions.size(), wanted)),
						latestTransactions.size()));
		
		Collections.reverse(lastTxs);
		
		return lastTxs;
	}
	
	public boolean hasAddressInfos(String addressUid) {
		return addresses.containsKey(addressUid);
	}
	
	public long getTransactionCount() {
		return transactionCount;
	}

}
