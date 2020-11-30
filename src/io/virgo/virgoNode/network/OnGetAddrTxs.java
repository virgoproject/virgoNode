package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Infos.AddressInfos;

public class OnGetAddrTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject addrTxsResp = new JSONObject();
		addrTxsResp.put("command", "addrTxs");
		
		JSONArray addrTxs = new JSONArray();
		
		JSONArray addresses = messageJson.getJSONArray("addresses");
		for(int i = 0; i < addresses.length(); i++) {
			String address = addresses.getString(i);
			
			JSONObject txs = new JSONObject();
			txs.put("address", address);
			
			if(Main.getDAG().infos.hasAddressInfos(address)) {
				AddressInfos addrInfos = Main.getDAG().infos.getAddressInfos(address);
				
				txs.put("inputs", new JSONArray(addrInfos.getInputTxs()));
				txs.put("outputs", new JSONArray(addrInfos.getOutputTxs()));
			} else {
				txs.put("inputs", new JSONArray());
				txs.put("outputs", new JSONArray());
			}
			
			addrTxs.put(txs);
		}
		
		addrTxsResp.put("addrTxs", addrTxs);
		
		peer.respondToMessage(addrTxsResp, messageJson);
		
	}
	
}
