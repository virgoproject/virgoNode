package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnGetTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray askedTxs = messageJson.getJSONArray("ids");
		
		JSONArray foundTxs = new JSONArray();
		
		for(int i = 0; i < askedTxs.length(); i++) {
			JSONObject txJSON = Main.getDAG().getTxJSON(askedTxs.getString(i));
			if(txJSON != null)
				foundTxs.put(txJSON);
		}

		if(foundTxs.length() == 0)
			return;
			
		JSONObject getTxsResp = new JSONObject();	
		getTxsResp.put("command", "txs");
		getTxsResp.put("txs", foundTxs);
		
		peer.respondToMessage(getTxsResp, messageJson);
		
	}
	
}
