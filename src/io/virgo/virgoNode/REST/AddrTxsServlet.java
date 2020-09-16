package io.virgo.virgoNode.REST;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Infos.AddressInfos;

public class AddrTxsServlet {

	public static Response GET(String[] arguments) {
		
		JSONObject txs = new JSONObject();
		txs.put("address", arguments[0]);
		
		if(Main.getDAG().infos.hasAddressInfos(arguments[0])) {
			AddressInfos addrInfos = Main.getDAG().infos.getAddressInfos(arguments[0]);
			
			txs.put("inputs", new JSONArray(addrInfos.getInputTxs()));
			txs.put("outputs", new JSONArray(addrInfos.getOutputTxs()));
		} else {
			txs.put("inputs", new JSONArray());
			txs.put("outputs", new JSONArray());
		}
		
		return new Response(200,txs.toString());
		
	}
	
}
