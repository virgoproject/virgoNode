package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;

public class TxServlet {

	public static Response GET(String[] arguments) {
		
		if(arguments.length == 0)
			return new Response(405, "");
		
		System.out.println(arguments[0]);
		
		JSONObject txState = new JSONObject();
		txState.put("tx", arguments[0]);
		
		if(Main.getDAG().isLoaded(arguments[0])) {
			LoadedTransaction tx = Main.getDAG().getLoadedTx(arguments[0]);
			
			txState.put("status", tx.getStatus().ordinal());
			txState.put("stability", tx.getStability());
			
			JSONArray txOutputs = new JSONArray();
			
			for(TxOutput out : tx.getOutputsMap().values()) {
				JSONObject outputState = new JSONObject();
				
				outputState.put("address", out.getAddress());
				outputState.put("amount", out.getAmount());
				outputState.put("state", out.isSpent());
				
				txOutputs.put(outputState);
			}
			
			txState.put("outputsState", txOutputs);
			
			return new Response(200, txState.toString());
			
		} else {
			txState.put("notFound", true);
			
			return new Response(404, txState.toString());
		}
		
		
	}
	
}
