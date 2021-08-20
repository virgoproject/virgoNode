package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnGetChilds {

	public static void handle(JSONObject messageJson, Peer peer) {
		try {
			Sha256Hash txHash = new Sha256Hash(messageJson.getString("txHash"));
			int wanted = messageJson.getInt("wanted");

			JSONArray childs = new JSONArray();
			for(Sha256Hash hash : Main.getDatabase().getInsertedAfter(txHash, wanted))
				childs.put(hash.toString());
						
			//superior to 1 because getInsertedAfter will always return atleast txHash
			if(childs.length() > 1) {
				JSONObject response = new JSONObject();	
				response.put("command", "childs");
				response.put("txHash", txHash.toString());
				response.put("childs", childs);
				
				JSONArray tips = new JSONArray();
				for(Sha256Hash tipHash : Main.getDAG().getTipsUids())
					tips.put(tipHash.toString());
				
				response.put("tips", tips);
				
				peer.respondToMessage(response, messageJson);
				
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
