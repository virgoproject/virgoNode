package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import io.virgo.virgoNode.Main;

public class TipsServlet {

	public static Response GET() {
		
		return new Response(200, new JSONArray(Main.getDAG().getTipsUids()).toString());
		
	}
	
}
