package io.virgo.virgoNode.network;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnParents {

	public static void handle(JSONObject messageJson, Peer peer) {
	
		try {
			
			JSONArray parents = messageJson.getJSONArray("parents");
			
			Sha256Hash maxAncestorHash = new Sha256Hash(messageJson.getString("maxAncestorHash"));
			boolean reachedEnd = false;
			
			ArrayList<Sha256Hash> lakingTxs = new ArrayList<Sha256Hash>();
			for(int i = 0; i < parents.length(); i++) {
				Sha256Hash parent = new Sha256Hash(parents.getString(i));
				
				if(parent.equals(maxAncestorHash))
					reachedEnd = true;
					
				if(!Main.getDAG().hasTransaction(parent))
					lakingTxs.add(parent);
					
			}
			
			if(lakingTxs.size() > 0)
				Peers.askTxs(lakingTxs);
								
			if(!reachedEnd)
				Peers.askParents(new Sha256Hash(parents.getString(parents.length()-1)), maxAncestorHash);
			
		}catch(Exception e) {}
		
	}
	
}
