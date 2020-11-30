package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Infos.AddressInfos;

public class OnGetBalances {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject balancesResp = new JSONObject();
		balancesResp.put("command", "balances");
		
		JSONArray balances = new JSONArray();
		
		JSONArray askedBalances = messageJson.getJSONArray("addresses");
		for(int i = 0; i < askedBalances.length(); i++) {
			String address = askedBalances.getString(i);
			
			JSONObject balance = new JSONObject();
			balance.put("address", address);
			
			if(Main.getDAG().infos.hasAddressInfos(address)) {
				AddressInfos addrInfos = Main.getDAG().infos.getAddressInfos(address);
				balance.put("received", addrInfos.getTotalReceived());
				balance.put("sent", addrInfos.getTotalSent());
			}else {
				balance.put("received", 0);
				balance.put("sent", 0);
			}
			
			balances.put(balance);
		}
		
		balancesResp.put("balances", balances);
		
		peer.respondToMessage(balancesResp, messageJson);
		
	}
	
}
