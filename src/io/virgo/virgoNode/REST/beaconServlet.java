package io.virgo.virgoNode.REST;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;

/**
 * REST API Beacon servlet
 * 	<br><br>
 *  GET Methods:<br>
 *  /beacon/latest/[count] <br>
 *  /beacon/{transactionHash}
 */
public class beaconServlet {

	public static Response GET(String[] arguments) {
		
		if(arguments.length >= 1) {
			if(arguments[0].equals("latest")) {
				
				int wanted = 10;
				
				try {
					if(arguments.length >= 2)
						wanted = Math.min(Math.abs(Integer.parseInt(arguments[1])), 100);
					
					ArrayList<Sha256Hash> beacons = new ArrayList<Sha256Hash>();
					LoadedTransaction currentBeacon = Main.getDAG().getBestTipBeacon().getParentBeacon();
					for(int i = 0; i < wanted; i++) {
						beacons.add(currentBeacon.getHash());
						if(currentBeacon.getParentBeacon() == null)
							break;
						
						currentBeacon = currentBeacon.getParentBeacon();
					}

					JSONArray resp = new JSONArray();
					
					for(Sha256Hash beaconHash : beacons)
						resp.put(beaconHash.toString());
					
					return new Response(200, resp.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
				
			}else {
				
				LoadedTransaction beacon = Main.getDAG().getLoadedTx(new Sha256Hash(arguments[0]));
				
				if(!beacon.isBeaconTransaction()) {
					JSONObject resp = new JSONObject();
					resp.put("notFound", true);
					
					return new Response(404, resp.toString());
				}
				
				JSONObject response = new JSONObject();
				response.put("parentBeacon", beacon.getParentBeacon().getHash());
				response.put("height", beacon.getBeaconHeight());
				response.put("difficulty", beacon.getDifficulty());
				response.put("isMainChainMember", beacon.isMainChainMember());
				response.put("randomXKey", beacon.getRandomXKey());
				response.put("weight", beacon.getWeight());
				response.put("confirmations", beacon.confirmationCount());
				
				return new Response(200, response.toString());
			}
		}
		
		return new Response(405, "");
		
	}
	
}
