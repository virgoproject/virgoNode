package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

public class WorkServlet {

	public static Response GET() {
		LoadedTransaction bestParentBeacon = Main.getDAG().getBestTipBeacon();
		
		JSONObject response = new JSONObject();
		response.put("parentBeacon", bestParentBeacon.getHash().toString());
		response.put("difficulty", bestParentBeacon.getDifficulty().toString());
		response.put("key", bestParentBeacon.getRandomXKey());
		
		JSONArray parentTxs = new JSONArray();
		for(Sha256Hash parent : Main.getDAG().getBestParents())
			parentTxs.put(parent.toString());
			
		response.put("parentTxs", parentTxs);
		
		return new Response(200, response.toString());
	}
	
}
