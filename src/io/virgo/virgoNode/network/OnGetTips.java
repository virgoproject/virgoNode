package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnGetTips {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject tipsMsg = new JSONObject();	
		tipsMsg.put("command", "tips");
		
		JSONArray tips = new JSONArray();
		
		for(Sha256Hash txHash : Main.getDAG().getTipsUids())
			tips.put(txHash.toString());
		
		tipsMsg.put("tips", tips);
		peer.respondToMessage(tipsMsg, messageJson);
	}
	
}
