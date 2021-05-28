package io.virgo.virgoNode.network;

import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnGetNodeInfos {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject nodeInfosResp = new JSONObject();
		nodeInfosResp.put("command", "nodeInfos");
		nodeInfosResp.put("appName", Main.getAppName());
		nodeInfosResp.put("netID", Main.getNetID());
		nodeInfosResp.put("DAGHeight", Main.getDAG().getBestTipBeacon().getBeaconHeight());
		nodeInfosResp.put("DAGWeight", Main.getDAG().getGenesis().getWeight());
		nodeInfosResp.put("poolSize", Main.getDAG().getPoolSize());
		
		peer.respondToMessage(nodeInfosResp, messageJson);
		
	}
	
}
