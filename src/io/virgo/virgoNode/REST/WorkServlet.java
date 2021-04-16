package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

public class WorkServlet {

	public static Response GET() {
		LoadedTransaction bestParentBeacon = Main.getDAG().getBestTipBeacon();
		
		JSONObject response = new JSONObject();
		response.put("parentBeacon", bestParentBeacon.getUid());
		response.put("difficulty", bestParentBeacon.getDifficulty().toString());
		response.put("key", bestParentBeacon.getRandomXKey());
		
		JSONArray parentTxs = new JSONArray();
		for(String parent : Main.getDAG().getBestParents())
			parentTxs.put(parent);
			
		response.put("parentTxs", parentTxs);
		
		return new Response(200, response.toString());
	}
	
}
