package io.virgo.virgoNode.REST;

import org.json.JSONArray;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class TipsServlet {

	public static Response GET() {
		
		JSONArray tips = new JSONArray();
		
		for(Sha256Hash txHash : Main.getDAG().getTipsUids())
			tips.put(txHash.toString());
		
		return new Response(200, tips.toString());
		
	}
	
}
