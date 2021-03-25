package io.virgo.virgoNode.REST;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Infos.AddressInfos;

public class AddressServlet {

	public static Response GET(String[] arguments) {
		
		if(arguments.length >= 2) {
			
			
			if(!Main.getDAG().infos.hasAddressInfos(arguments[0])) {
				JSONObject resp = new JSONObject();
				resp.put("notFound", true);
				
				return new Response(404, resp.toString());
			}
				
			AddressInfos infos = Main.getDAG().infos.getAddressInfos(arguments[0]);
			
			switch(arguments[1]) {
			
			case "txs":
				try {
					
					int perPage = 10;
					int pages = 1;
					if(arguments.length >= 3) {
						perPage = Math.abs(Integer.parseInt(arguments[2]));
						if(arguments.length >= 4)
							pages = Math.abs(Integer.parseInt(arguments[3]));
					}
					
					ArrayList<String> inputs = infos.getInputs();
					JSONArray inputsJSON = new JSONArray(inputs.subList(Math.min((pages-1)*perPage, inputs.size()-1), Math.min(pages*perPage, inputs.size())));
					
					ArrayList<String> outputs = infos.getInputs();
					JSONArray outputsJSON = new JSONArray(outputs.subList(Math.min((pages-1)*perPage, outputs.size()-1), Math.min(pages*perPage, outputs.size())));
					
					JSONObject response = new JSONObject();
					response.put("inputs", inputsJSON);
					response.put("inputs", outputsJSON);
					
					
					return new Response(200,response.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
				
			case "balance":
				JSONObject balance = new JSONObject();
				balance.put("address", arguments[0]);
				balance.put("received", infos.getTotalReceived());
				balance.put("sent", infos.getTotalSent());
				
				return new Response(200, balance.toString());
				
				default: return new Response(405, "");
					
				
			}
			
		}else return new Response(405, "");
		
		
	}
	
}
