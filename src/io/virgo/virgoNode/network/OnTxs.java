package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("txs");
				
		for(int i = 0; i < txs.length(); i++) {
			
			JSONObject txJson = txs.getJSONObject(i);
			
			Main.getDAG().verificationPool. new jsonVerificationTask(txJson, false);
			
		}
		
	}
	
}
