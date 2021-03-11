package io.virgo.virgoNode.network;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;

public class OnGetTxsState {

	public static void handle(JSONObject messageJson, Peer peer) {
		
		JSONObject txsStateResp = new JSONObject();
		txsStateResp.put("command", "txsState");
		
		JSONArray txsState = new JSONArray();
		
		JSONArray txsUids = messageJson.getJSONArray("txs");
		for(int i = 0; i < txsUids.length(); i++) {
			String uid = txsUids.getString(i);
			
			JSONObject txState = new JSONObject();
			txState.put("tx", uid);
			
			if(Main.getDAG().isLoaded(uid)) {
				LoadedTransaction tx = Main.getDAG().getLoadedTx(uid);
				
				txState.put("status", tx.getStatus().ordinal());
				txState.put("confirmations", tx.confirmationCount());

				JSONArray txOutputs = new JSONArray();
				
				for(TxOutput out : tx.getOutputsMap().values()) {
					JSONObject outputState = new JSONObject();
					
					outputState.put("address", out.getAddress());
					outputState.put("amount", out.getAmount());
					outputState.put("state", out.isSpent());
					
					txOutputs.put(outputState);
				}
				
				txState.put("outputsState", txOutputs);
			}else {
				txState.put("notLoaded", true);
			}
			
			txsState.put(txState);
		}
		
		txsStateResp.put("txsState", txsState);
		
		peer.respondToMessage(txsStateResp, messageJson);
		
	}
	
}
