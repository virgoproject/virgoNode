package io.virgo.virgoNode.REST;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
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
					
					List<Sha256Hash> transactions = infos.getTransactions();
					JSONArray transactionsJSON = new JSONArray();

					if(transactions.size() != 0)
						transactions = transactions.subList(Math.min((pages-1)*perPage, transactions.size()-1), Math.min(pages*perPage, transactions.size()));
						
					for(Sha256Hash txHash : transactions)
						transactionsJSON.put(txHash.toString());
					
					JSONObject response = new JSONObject();
					response.put("txs", transactionsJSON);
					response.put("size", transactions.size());
					
					return new Response(200,response.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
			
			case "inputs":
				try {
					
					int perPage = 10;
					int pages = 1;
					if(arguments.length >= 3) {
						perPage = Math.abs(Integer.parseInt(arguments[2]));
						if(arguments.length >= 4)
							pages = Math.abs(Integer.parseInt(arguments[3]));
					}
					
					List<Sha256Hash> inputs = infos.getInputs();
					JSONArray inputsJSON = new JSONArray();

					if(inputs.size() != 0)
						inputs = inputs.subList(Math.min((pages-1)*perPage, inputs.size()-1), Math.min(pages*perPage, inputs.size()));
					
					for(Sha256Hash txHash : inputs)
						inputsJSON.put(txHash.toString());
						
					JSONObject response = new JSONObject();
					response.put("inputs", inputsJSON);
					response.put("size", inputs.size());
					
					return new Response(200,response.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
			
			case "outputs":
				try {
					
					int perPage = 10;
					int pages = 1;
					if(arguments.length >= 3) {
						perPage = Math.abs(Integer.parseInt(arguments[2]));
						if(arguments.length >= 4)
							pages = Math.abs(Integer.parseInt(arguments[3]));
					}
					
					List<Sha256Hash> outputs = infos.getOutputs();
					JSONArray outputsJSON = new JSONArray();

					if(outputs.size() != 0)
						outputs = outputs.subList(Math.min((pages-1)*perPage, outputs.size()-1), Math.min(pages*perPage, outputs.size()));
						
					for(Sha256Hash txHash : outputs)
						outputsJSON.put(txHash.toString());
					
					JSONObject response = new JSONObject();
					response.put("outputs", outputsJSON);
					response.put("size", outputs.size());
					
					
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
