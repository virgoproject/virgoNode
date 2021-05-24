package io.virgo.virgoNode.network;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.geoWeb.Peer;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

public class OnTxs {

	public static void handle(JSONObject messageJson, Peer peer) {
		JSONArray txs = messageJson.getJSONArray("txs");
		
		JSONArray transactionsToBroadcast = new JSONArray();
		
		for(int i = 0; i < txs.length(); i++) {
			
			JSONObject txJson = txs.getJSONObject(i);
		
			Sha256Hash txHash;
			
			if(txJson.has("parentBeacon")) {
				txHash = Sha256.getDoubleHash((txJson.getJSONArray("parents").toString()
						+ txJson.getJSONArray("outputs").toString()
						+ txJson.getString("parentBeacon")
						+ txJson.getLong("date")
						+ txJson.getLong("nonce")).getBytes());
			}else {
				txHash = Sha256.getDoubleHash(Converter.concatByteArrays(
						(txJson.getJSONArray("parents").toString() + txJson.getJSONArray("inputs").toString() + txJson.getJSONArray("outputs").toString()).getBytes(),
						Converter.hexToBytes(txJson.getString("sig")), Converter.hexToBytes(txJson.getString("pubKey")), Miscellaneous.longToBytes(txJson.getLong("date"))));
			}
			
			
			if(Main.getDAG().hasTransaction(txHash))
				continue;
			

			try {
				Main.getDAG().initTx(txJson, false);
				
				if(messageJson.has("callback")) {
					JSONObject txCallback = new JSONObject();	
					txCallback.put("command", "txCallback");
					txCallback.put("id", txHash.toString());
					txCallback.put("result", true);
					peer.respondToMessage(txCallback, messageJson);
				}
				
				transactionsToBroadcast.put(txHash.toString());
				
			}catch(IllegalArgumentException e) {
				e.printStackTrace();
				if(messageJson.has("callback")) {
					JSONObject txCallback = new JSONObject();	
					txCallback.put("command", "txCallback");
					txCallback.put("id", txHash);
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
