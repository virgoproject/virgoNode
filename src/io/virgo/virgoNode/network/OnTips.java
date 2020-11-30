package io.virgo.virgoNode.network;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;

public class OnTips {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONArray tips = messageJson.getJSONArray("tips");
		
		System.out.println("received tips " + tips);
		
		ArrayList<String> lakingTxs = new ArrayList<String>();
		
		for(int i = 0; i < tips.length(); i++) {
			String tx = tips.getString(i);
			if(!Main.getDAG().hasTransaction(tx))
				lakingTxs.add(tx);
		}
		
		if(lakingTxs.size() != 0)
			Peers.askTxs(lakingTxs);
		
	}
	
}
