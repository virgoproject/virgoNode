package io.virgo.virgoNode.network;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoNode.Main;

public class OnTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("txs");
		
		JSONArray transactionsToBroadcast = new JSONArray();
		
		for(int i = 0; i < txs.length(); i++) {
			
			JSONObject txJson = txs.getJSONObject(i);
		
			String txUid;
			
			if(txJson.has("parentBeacon")) {
				txUid = Converter.Addressify(Sha256.getDoubleHash((txJson.getJSONArray("parents").toString()
						+ txJson.getJSONArray("outputs").toString()
						+ txJson.getString("parentBeacon")
						+ txJson.getLong("date")
						+ txJson.getLong("nonce")).getBytes()).toBytes(), Main.TX_IDENTIFIER);
			}else {
				txUid = Converter.Addressify(Converter.hexToBytes(txJson.getString("sig")), Main.TX_IDENTIFIER);
			}
			
			
			if(Main.getDAG().hasTransaction(txUid))
				continue;

			try {
				Main.getDAG().initTx(txJson, false);
				
				if(messageJson.has("callback")) {
					JSONObject txCallback = new JSONObject();	
					txCallback.put("command", "txCallback");
					txCallback.put("id", txUid);
					txCallback.put("result", true);
					peer.respondToMessage(txCallback, messageJson);
				}
				
				transactionsToBroadcast.put(txUid);
				
			}catch(IllegalArgumentException e) {
				e.printStackTrace();
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
