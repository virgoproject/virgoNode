package io.virgo.virgoNode.REST;

import org.json.JSONObject;

import io.virgo.virgoNode.Main;

public class NodeInfosServlet {

	public static Response GET() {
		
		JSONObject nodeInfosResp = new JSONObject();
		nodeInfosResp.put("appName", Main.getAppName());
		nodeInfosResp.put("netID", Main.getNetID());
		nodeInfosResp.put("BeaconChainWeight", Main.getDAG().getGenesis().getWeight());
		nodeInfosResp.put("BeaconChainHeight", Main.getDAG().getBestTipBeacon().getBeaconHeight()+1);
		nodeInfosResp.put("DAGHeight", Main.getDAG().getLoadedTx(Main.getDAG().getTipsUids().get(0)).getHeight()+1);
		nodeInfosResp.put("DAGSize", Main.getDAG().loadedTxsCount());
		
		return new Response(200, nodeInfosResp.toString());
	}
	
}
