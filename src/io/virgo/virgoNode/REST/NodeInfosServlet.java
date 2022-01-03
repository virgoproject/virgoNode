package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

/**
 * REST API Node Infos servlet
 * 	<br><br>
 *  GET Methods:<br>
 *  /nodeinfos
 */
public class NodeInfosServlet {

	public static Response GET() {
		
		JSONObject nodeInfosResp = new JSONObject();
		nodeInfosResp.put("appName", Main.getAppName());
		nodeInfosResp.put("netID", Main.getNetID());
		nodeInfosResp.put("BeaconChainWeight", Main.getDAG().getBestTipBeacon().getFloorWeight());
		nodeInfosResp.put("BeaconChainHeight", Main.getDAG().getBestTipBeacon().getBeaconHeight()+1);
		nodeInfosResp.put("DAGHeight", Main.getDAG().getLoadedTx(Main.getDAG().getTipsUids().get(0)).getHeight()+1);
		nodeInfosResp.put("DAGSize", Main.getDAG().loadedTxsCount());
		
		JSONArray peers = new JSONArray();
		for(Peer peer : Main.getGeoWeb().getPeers())
			peers.put(peer.getEffectiveAddress());
		
		nodeInfosResp.put("peers", peers);
		
		return new Response(200, nodeInfosResp.toString());
	}
	
}
