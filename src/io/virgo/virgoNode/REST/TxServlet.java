package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.Transaction;
import io.virgo.virgoNode.DAG.TxOutput;

/**
 * REST API Transaction servlet
 * 	<br><br>
 *  GET Methods:<br>
 *  /tx/latest/[count] <br>
 *  /tx/{transactionHash} <br>
 *  /tx/{transactionHash}/state
 *  <br><br>
 *  POST Methods:<br>
 *  /tx (request body: transaction JSON)
 */
public class TxServlet {

	public static Response GET(String[] arguments) {
		
		switch(arguments.length) {
		
		case 1:
			if(arguments[0].equals("latest")) {
				
				JSONArray resp = new JSONArray();
				
				for(Sha256Hash txHash : Main.getDAG().infos.getLatestTransactions(10))
					resp.put(txHash.toString());
				
				return new Response(200, resp.toString());
				
			}else {
				
				Sha256Hash txHash = new Sha256Hash(arguments[0]);
				
				if(Main.getDAG().hasTransaction(txHash))
					return new Response(200, Main.getDAG().getTxJSON(txHash).toString());
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
					
					JSONArray resp = new JSONArray();
					
					for(Sha256Hash txHash : Main.getDAG().infos.getLatestTransactions(wanted))
						resp.put(txHash.toString());
					
					return new Response(200, resp.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
				
			}else {
				
				switch(arguments[1]) {
				
				case "state":
					LoadedTransaction tx = Main.getDAG().getLoadedTx(new Sha256Hash(arguments[0]));
					
					if(tx != null) {
						JSONObject txState = new JSONObject();
						
						txState.put("status", tx.getStatus().ordinal());
						txState.put("confirmations", tx.confirmationCount());
						
						Transaction settler = tx.getSettlingTransaction();
						
						if(settler != null)
							txState.put("beacon", settler.getHash().toString());
						else
							txState.put("beacon", "");
						
						JSONArray txOutputs = new JSONArray();
						
						for(TxOutput out : tx.getOutputsMap().values()) {
							JSONObject outputState = new JSONObject();
							
							outputState.put("address", out.getAddress());
							outputState.put("amount", out.getAmount());
							outputState.put("spent", out.isSpent());
							
							JSONArray outClaimers = new JSONArray();
							
							for(Transaction claimer : out.claimers) {
								JSONObject outClaimer = new JSONObject();
								outClaimer.put("id", claimer.getHash().toString());
								outClaimer.put("status", claimer.getLoaded().getStatus().getCode());
								outClaimers.put(outClaimer);
							}
							
							outputState.put("claimers", outClaimers);
							
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
			
		case 3:
			if(arguments[0].equals("latest")) {
				
				try {
					int wanted = Integer.parseInt(arguments[1]);
					
					JSONArray resp = new JSONArray();
					
					for(Sha256Hash txHash : Main.getDAG().infos.getLatestTransactions(wanted))
						resp.put(txHash.toString());
					
					return new Response(200, resp.toString());
					
				}catch(NumberFormatException e) {
					return new Response(405, "");
				}
			}
			
		default: return new Response(405, "");
		
		}
		
	}

	public static Response POST(String[] arguments, String requestBody) {
		try {
			JSONObject txJSON = new JSONObject(requestBody);
			Main.getDAG().verificationPool. new jsonVerificationTask(txJSON, false, true);
			return new Response(200, "");
		}catch(JSONException|IllegalArgumentException e) {
			return new Response(405, "");
		}
	}
	
}
