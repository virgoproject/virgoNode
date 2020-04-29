package io.virgo.virgoNode.DAG.Infos;

import java.util.concurrent.ConcurrentHashMap;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

public class DAGInfos {

	final private ConcurrentHashMap<String, AddressInfos> addresses = new ConcurrentHashMap<String, AddressInfos>(); 
	
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
	
	public boolean hasAddressInfos(String addressUid) {
		return addresses.containsKey(addressUid);
	}
	
	public long getTransactionCount() {
		return transactionCount;
	}

}
