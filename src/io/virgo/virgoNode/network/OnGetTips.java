package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnGetTips {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject tipsMsg = new JSONObject();	
		tipsMsg.put("command", "tips");
		tipsMsg.put("tips", new JSONArray(Main.getDAG().getTipsUids()));
		peer.respondToMessage(tipsMsg, messageJson);
	}
	
}
