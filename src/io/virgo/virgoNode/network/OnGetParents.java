package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnGetParents {

	public static void handle(JSONObject messageJson, Peer peer) {

		try {
			Sha256Hash txHash = new Sha256Hash(messageJson.getString("txHash"));
			Sha256Hash maxAncestorHash = new Sha256Hash(messageJson.getString("maxAncestorHash"));
			
			JSONArray parents = new JSONArray();
			for(Sha256Hash hash : Main.getDatabase().getInsertedBefore(txHash, maxAncestorHash))
				parents.put(hash.toString());
			
			if(parents.length() > 0) {
				
				JSONObject response = new JSONObject();	
				response.put("command", "childs");
				response.put("txHash", txHash.toString());
				response.put("maxAncestorHash", maxAncestorHash.toString());
				response.put("parents", parents);
				
				peer.respondToMessage(response, messageJson);
								
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
}
