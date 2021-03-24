package io.virgo.virgoNode.REST;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

public class beaconServlet {

	public static Response GET(String[] arguments) {
		
		if(arguments.length >= 1) {
			if(arguments[0].equals("latest")) {
				
				int wanted = 10;
				
				try {
					if(arguments.length >= 2)
						wanted = Math.min(Math.abs(Integer.parseInt(arguments[1])), 100);
					
					ArrayList<String> beacons = new ArrayList<String>();
					LoadedTransaction currentBeacon = Main.getDAG().getBestTipBeacon().getParentBeacon();
					for(int i = 0; i < wanted; i++) {
						beacons.add(currentBeacon.getUid());
						if(currentBeacon.getParentBeacon() == null)
							break;
						
						currentBeacon = currentBeacon.getParentBeacon();
					}

					JSONArray resp = new JSONArray(beacons);
					return new Response(200, resp.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
				
			}else {
				
				LoadedTransaction beacon = Main.getDAG().getLoadedTx(arguments[0]);
				
				if(!beacon.isBeaconTransaction()) {
					JSONObject resp = new JSONObject();
					resp.put("notFound", true);
					
					return new Response(404, resp.toString());
				}
				
				JSONObject response = new JSONObject();
				response.put("parentBeacon", beacon.getParentBeacon().getUid());
				response.put("height", beacon.getBeaconHeight());
				response.put("difficulty", beacon.getDifficulty());
				response.put("isMainChainMember", beacon.isMainChainMember());
				response.put("randomXKey", beacon.getRandomXKey());
				
				return new Response(200, response.toString());
			}
		}
		
		return new Response(405, "");
		
	}
	
}
