package io.virgo.virgoNode.network;

import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.GeoWeb;

public class Peers {

	public static void getTips() {
		
		System.out.println("we ask for tips");
		
		JSONObject message = new JSONObject();
		message.put("command", "getTips");
		
		GeoWeb.getInstance().broadCast(message);
		
	}
	
	public static void askTxs(Collection<String> ids) {
		
		System.out.println("we ask for " + new JSONArray(ids));
		
		JSONObject message = new JSONObject();
		message.put("command", "askTxs");
		message.put("ids", new JSONArray(ids));
		
		GeoWeb.getInstance().broadCast(message);
		
	}
	
}
