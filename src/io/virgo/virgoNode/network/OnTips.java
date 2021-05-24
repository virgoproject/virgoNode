package io.virgo.virgoNode.network;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

public class OnTips {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONArray tips = messageJson.getJSONArray("tips");
		
		ArrayList<Sha256Hash> lakingTxs = new ArrayList<Sha256Hash>();
		
		for(int i = 0; i < tips.length(); i++) {
			Sha256Hash txHash = new Sha256Hash(tips.getString(i));
			if(!Main.getDAG().hasTransaction(txHash))
				lakingTxs.add(txHash);
		}
		
		if(lakingTxs.size() != 0)
			Peers.askTxs(lakingTxs);
		
	}
	
}
