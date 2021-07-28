package io.virgo.virgoNode.network;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnChilds {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		try {
			
			JSONArray childs = messageJson.getJSONArray("childs");
			
			ArrayList<Sha256Hash> lakingTxs = new ArrayList<Sha256Hash>();
			for(int i = 0; i < childs.length(); i++) {
				Sha256Hash child = new Sha256Hash(childs.getString(i));
				
				if(!Main.getDAG().hasTransaction(child))
					lakingTxs.add(child);
					
			}
			
			if(lakingTxs.size() > 0)
				Peers.askTxs(lakingTxs);
								
			JSONArray tips = messageJson.getJSONArray("tips");
			
			for(int i = 0; i < tips.length(); i++) {
				Sha256Hash tip = new Sha256Hash(tips.getString(i));
				
				if(!Main.getDAG().hasTransaction(tip) && !lakingTxs.contains(tip))
					Peers.askChilds(lakingTxs.get(lakingTxs.size()-1));
			}
			
		}catch(Exception e) {}
		
	}
	
}
