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

			JSONArray childsJSON = messageJson.getJSONArray("childs");
			
			ArrayList<String> childs = new ArrayList<String>();
			for(int i = 0; i < childsJSON.length(); i++)
				childs.add(childsJSON.getString(i));
			
			ArrayList<String> lakingTxs = new ArrayList<String>();
			for(String childHashStr : childs) {
				Sha256Hash child = new Sha256Hash(childHashStr);
				
				if(!Main.getDAG().hasTransaction(child))
					lakingTxs.add(childHashStr);				
			}
			
			if(lakingTxs.size() > 0) {
				JSONObject message = new JSONObject();
				message.put("command", "askTxs");
				message.put("ids", new JSONArray(lakingTxs));
				
				peer.sendMessage(message);
				
				JSONArray tips = messageJson.getJSONArray("tips");
				
				for(int i = 0; i < tips.length(); i++) {
					Sha256Hash tip = new Sha256Hash(tips.getString(i));
					
					if(!Main.getDAG().isLoaded(tip) && !childs.contains(tip.toString())) {
						Peers.askChilds(new Sha256Hash(childs.get(childs.size()-1)), 2000);
						break;
					}
				}
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
