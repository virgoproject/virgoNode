package io.virgo.virgoNode.network;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.GeoWeb;
import io.virgo.geoWeb.Peer;
import io.virgo.geoWeb.ResponseCode;
import io.virgo.geoWeb.SyncMessageResponse;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnParents {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		try {
			
			JSONArray parents = messageJson.getJSONArray("parents");
			
			Sha256Hash maxAncestorHash = new Sha256Hash(messageJson.getString("maxAncestorHash"));
			boolean reachedEnd = false;
			
			ArrayList<String> lakingTxs = new ArrayList<String>();
			for(int i = 0; i < parents.length(); i++) {
				Sha256Hash parent = new Sha256Hash(parents.getString(i));
				
				if(parent.equals(maxAncestorHash))
					reachedEnd = true;
					
				if(!Main.getDAG().hasTransaction(parent))
					lakingTxs.add(parent.toString());
					
			}
			
			if(lakingTxs.size() > 0) {
				JSONObject message = new JSONObject();
				message.put("command", "askTxs");
				message.put("ids", new JSONArray(lakingTxs));
				
				peer.sendMessage(message);
				
				if(!reachedEnd)
					Peers.askParents(new Sha256Hash(parents.getString(parents.length()-1)), maxAncestorHash, 500);
				
			}
								
		}catch(Exception e) {}
		
	}
	
}
