package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;

public class TipsServlet {

	public static Response GET() {
		
		JSONObject response = new JSONObject();
		response.put("tips", new JSONArray(Main.getDAG().getTipsUids()));
		
		return new Response(200, response.toString());
		
	}
	
}
