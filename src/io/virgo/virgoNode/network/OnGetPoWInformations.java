package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

public class OnGetPoWInformations {

	public static void handle(JSONObject messageJson, Peer peer) {
		LoadedTransaction bestParentBeacon = Main.getDAG().getBestTipBeacon();
		
		JSONObject resp = new JSONObject();
		resp.put("command", "PoWInformations");
		resp.put("parentBeacon", bestParentBeacon.getUid());
		resp.put("difficulty", bestParentBeacon.getDifficulty().toString());
		resp.put("key", bestParentBeacon.getRandomXKey());
		
		JSONArray parentTxs = new JSONArray();
		for(String parent : Main.getDAG().getBestParents())
			parentTxs.put(parent);
			
		resp.put("parentTxs", parentTxs);
		
		peer.respondToMessage(resp, messageJson);
	}
	
}
