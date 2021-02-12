package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnInv {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("ids");
		
		JSONArray wantedTxs = new JSONArray();
		
		for(int i = 0; i < txs.length(); i++) {
			String txId = txs.getString(i);
			if(!Main.getDAG().hasTransaction(txId))
				wantedTxs.put(txId);
		}
		
		if(wantedTxs.length() == 0)
			return;
		
		JSONObject invResp = new JSONObject();	
		invResp.put("command", "getTxs");
		invResp.put("ids", wantedTxs);
		
		peer.respondToMessage(invResp, messageJson);
		
	}
	
}
