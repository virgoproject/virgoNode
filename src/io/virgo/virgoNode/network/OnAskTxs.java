package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnAskTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("ids");
		
		JSONArray foundTxs = new JSONArray();
		
		for(int i = 0; i < txs.length(); i++) {
			Sha256Hash txHash = new Sha256Hash(txs.getString(i));
			
			if(Main.getDAG().hasTransaction(txHash))
				foundTxs.put(txHash.toString());
			
		}
		
		if(foundTxs.length() == 0)
			return;
		
		JSONObject response = new JSONObject();	
		response.put("command", "inv");
		response.put("ids", foundTxs);
		
		peer.respondToMessage(response, messageJson);
		
	}
	
}
