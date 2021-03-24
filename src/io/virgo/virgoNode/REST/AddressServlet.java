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
					ArrayList<String> transactions = infos.getTransactions();
					JSONArray txs = new JSONArray(transactions.subList(Math.min((pages-1)*perPage, transactions.size()-1), Math.min(pages*perPage, transactions.size())));
					return new Response(200,txs.toString());
					
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
