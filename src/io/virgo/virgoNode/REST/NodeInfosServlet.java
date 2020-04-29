package io.virgo.virgoNode.REST;

import org.json.JSONObject;

import io.virgo.virgoNode.Main;

public class NodeInfosServlet {

	public static String GET() {
		
		JSONObject nodeInfosResp = new JSONObject();
		nodeInfosResp.put("command", "nodeInfos");
		nodeInfosResp.put("appName", Main.getAppName());
		nodeInfosResp.put("netID", Main.getNetID());
		nodeInfosResp.put("DAGHeight", Main.getDAG().getMainChainLength());
		nodeInfosResp.put("DAGWeight", Main.getDAG().infos.getTransactionCount());
		nodeInfosResp.put("poolSize", Main.getDAG().getPoolSize());
		
		return nodeInfosResp.toString();
	}
	
}
