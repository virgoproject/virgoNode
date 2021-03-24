package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;

public class TxServlet {

	public static Response GET(String[] arguments) {
		
		switch(arguments.length) {
		
		case 1:
			if(arguments[0].equals("latest")) {
				
				JSONArray resp = new JSONArray(Main.getDAG().infos.getLatestTransactions(10));
				return new Response(200, resp.toString());
				
			}else {
				
				if(Main.getDAG().hasTransaction(arguments[0]))
					return new Response(200, Main.getDAG().getTxJSON(arguments[0]).toString());
				else {
					JSONObject resp = new JSONObject();
					resp.put("notFound", true);
					
					return new Response(404, resp.toString());
				}
			}
			
		case 2:
			
			if(arguments[0].equals("latest")) {
				
				try {
					int wanted = Integer.parseInt(arguments[1]);
					
					JSONArray resp = new JSONArray(Main.getDAG().infos.getLatestTransactions(wanted));
					return new Response(200, resp.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
				
			}else {
				
				switch(arguments[1]) {
				
				case "state":
					LoadedTransaction tx = Main.getDAG().getLoadedTx(arguments[0]);
					
					if(tx != null) {
						JSONObject txState = new JSONObject();
						
						txState.put("status", tx.getStatus().ordinal());
						txState.put("confirmations", tx.confirmationCount());
						txState.put("beacon", tx.getSettlingTransaction().getUid());
						
						JSONArray txOutputs = new JSONArray();
						
						for(TxOutput out : tx.getOutputsMap().values()) {
							JSONObject outputState = new JSONObject();
							
							outputState.put("address", out.getAddress());
							outputState.put("amount", out.getAmount());
							outputState.put("spent", out.isSpent());
							
							txOutputs.put(outputState);
						}
						
						txState.put("outputsState", txOutputs);
						
						return new Response(200, txState.toString());
						
					} else {
						JSONObject resp = new JSONObject();
						resp.put("notFound", true);
						
						return new Response(404, resp.toString());
					}
				
				default: return new Response(405, "");
				
				}
				
			}
			
		default: return new Response(405, "");
		
		}		
		
	}
	
}
