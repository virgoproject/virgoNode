package io.virgo.virgoNode.REST;

import org.json.JSONObject;

import io.virgo.virgoNode.Main;

public class NodeInfosServlet {

	public static Response GET() {
		
		JSONObject nodeInfosResp = new JSONObject();
		nodeInfosResp.put("appName", Main.getAppName());
		nodeInfosResp.put("netID", Main.getNetID());
		nodeInfosResp.put("DAGHeight", Main.getDAG().getBestTipBeacon().getBeaconHeight());
		nodeInfosResp.put("DAGWeight", Main.getDAG().getGenesis().getWeight());
		nodeInfosResp.put("poolSize", Main.getDAG().getPoolSize());
		
		return new Response(200, nodeInfosResp.toString());
	}
	
}
