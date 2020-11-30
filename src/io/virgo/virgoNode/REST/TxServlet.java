package io.virgo.virgoNode.REST;

import org.json.JSONObject;

import io.virgo.virgoNode.Main;

public class TxServlet {

	public static Response GET(String[] arguments) {
		
		if(arguments.length == 0)
			return new Response(405, "");
		
		System.out.println(arguments[0]);
		
		JSONObject txState = new JSONObject();
		txState.put("tx", arguments[0]);
		
		if(Main.getDAG().hasTransaction(arguments[0])) {

			return new Response(200, Main.getDAG().getTxJSON(arguments[0]).toString());
			
		} else {
			txState.put("notFound", true);
			
			return new Response(404, txState.toString());
		}
		
		
	}
	
}
