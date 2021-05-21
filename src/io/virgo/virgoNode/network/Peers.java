package io.virgo.virgoNode.network;

import java.util.ArrayList;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.GeoWeb;
import io.virgo.virgoCryptoLib.Sha256Hash;

public class Peers {

	public static void getTips() {
		
		JSONObject message = new JSONObject();
		message.put("command", "getTips");
		
		GeoWeb.getInstance().broadCast(message);
		
	}
	
	public static void askTxs(Collection<Sha256Hash> txHashes) {
		
		ArrayList<String> hashesStrings = new ArrayList<String>();
		
		for(Sha256Hash txHash : txHashes)
			hashesStrings.add(txHash.toString());
		
		JSONObject message = new JSONObject();
		message.put("command", "askTxs");
		message.put("ids", new JSONArray(hashesStrings));
		
		GeoWeb.getInstance().broadCast(message);
		
	}
	
}
