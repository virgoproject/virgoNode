package io.virgo.virgoNode.Data;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.network.Peers;

public class TxLoader implements Runnable{

	DAG dag;
	LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();

	public TxLoader(DAG dag) {
		this.dag = dag;
	}
	
	@Override
	public void run() {
		while(true) {
			
			try {
				String txUid = queue.take();
				try {
					
					JSONObject txJSON = Main.getDatabase().getTx(txUid);
					
					if(txJSON != null) {
						byte[] sigBytes = Converter.hexToBytes(txJSON.getString("sig"));
						byte[] pubKey = Converter.hexToBytes(txJSON.getString("pubKey"));
						JSONArray parents = txJSON.getJSONArray("parents");
						JSONArray inputs = txJSON.getJSONArray("inputs");
						JSONArray outputs = txJSON.getJSONArray("outputs");
						long date = txJSON.getLong("date");
						
						dag.initTx(sigBytes, pubKey, parents, inputs, outputs, date, true);
						
					} else throw new JSONException("");
					
				} catch (JSONException | SQLException | IllegalArgumentException e) {
					Peers.askTxs(Arrays.asList(txUid));
					e.printStackTrace();
				}				
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}

	public void push(String tx) {
		if(!queue.contains(tx) && !dag.isTxWaiting(tx) && !dag.isLoaded(tx))
			queue.add(tx);
	}
	
	public void push(Collection<String> txs) {
		for(String tx : txs) {
			if(!queue.contains(tx) && !dag.isTxWaiting(tx))
				queue.add(tx);
		}
	}
}
