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
			JSONObject txJSON = Main.getDAG().getTxJSON(new Sha256Hash(txs.getString(i)));
			if(txJSON != null)
				foundTxs.put(txJSON);
		}

		if(foundTxs.length() == 0)
			return;
			
		JSONObject resp = new JSONObject();	
		resp.put("command", "txs");
		resp.put("txs", foundTxs);
		
		peer.respondToMessage(resp, messageJson);
		
	}
	
}
