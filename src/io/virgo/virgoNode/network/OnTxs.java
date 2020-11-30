package io.virgo.virgoNode.network;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoNode.Main;

public class OnTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("txs");
		
		JSONArray transactionsToBroadcast = new JSONArray();
		
		for(int i = 0; i < txs.length(); i++) {
			
			JSONObject txJson = txs.getJSONObject(i);
		
			String txUid = Converter.Addressify(Converter.hexToBytes(txJson.getString("sig")), Main.TX_IDENTIFIER);
			
			System.out.println("received " + txUid);
			
			if(Main.getDAG().hasTransaction(txUid))
				continue;
			
			byte[] sigBytes = Converter.hexToBytes(txJson.getString("sig"));
			byte[] pubKey = Converter.hexToBytes(txJson.getString("pubKey"));
			JSONArray parents = txJson.getJSONArray("parents");
			JSONArray inputs = txJson.getJSONArray("inputs");
			JSONArray outputs = txJson.getJSONArray("outputs");
			long date = txJson.getLong("date");
			
			try {
				Main.getDAG().initTx(sigBytes, pubKey, parents, inputs, outputs, date, false);
				
				if(messageJson.has("callback")) {
					JSONObject txCallback = new JSONObject();	
					txCallback.put("command", "txCallback");
					txCallback.put("id", txUid);
					txCallback.put("result", true);
					peer.respondToMessage(txCallback, messageJson);
				}
				
				transactionsToBroadcast.put(txUid);
				
			}catch(IllegalArgumentException e) {
				if(messageJson.has("callback")) {
					JSONObject txCallback = new JSONObject();	
					txCallback.put("command", "txCallback");
					txCallback.put("id", txUid);
					txCallback.put("result", false);
					peer.respondToMessage(txCallback, messageJson);
				}
			}
			
		}
		
		if(transactionsToBroadcast.length() == 0)
			return;
		
		//transmit tx to other peers
		JSONObject txInv = new JSONObject();	
		txInv.put("command", "inv");
		txInv.put("ids", transactionsToBroadcast);
		
		Main.getGeoWeb().broadCast(txInv, Arrays.asList(peer));
		
	}
	
}
