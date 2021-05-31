package io.virgo.virgoNode.Data;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.network.Peers;

/**
 * Runnable loading transactions from disk
 * Ask the network  requested transaction if not on disk
 */
public class TxLoader implements Runnable{

	DAG dag;
	LinkedBlockingQueue<Sha256Hash> queue = new LinkedBlockingQueue<Sha256Hash>();

	public TxLoader(DAG dag) {
		this.dag = dag;
	}
	
	@Override
	public void run() {
		while(true) {
			
			try {
				Sha256Hash txUid = queue.take();
				try {
					
					JSONObject txJSON = Main.getDatabase().getTx(txUid);
					
					if(txJSON != null)
						dag.verificationPool. new jsonVerificationTask(txJSON, true);
					else throw new JSONException("");
					
				} catch (JSONException | SQLException | IllegalArgumentException e) {
					Peers.askTxs(Arrays.asList(txUid));
				}				
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}

	/**
	 * Request to load a transaction
	 * The runnable will load it if present in database, else it will ask the network for it
	 */
	public void push(Sha256Hash tx) {
		if(!queue.contains(tx) && !dag.isTxWaiting(tx) && !dag.isLoaded(tx))
			queue.add(tx);
	}
	
	/**
	 * Request to load a set of transaction
	 * The runnable will load them if present in database, else it will ask the network for the missing ones
	 */
	public void push(Collection<Sha256Hash> txs) {
		for(Sha256Hash tx : txs) {
			if(!queue.contains(tx) && !dag.isTxWaiting(tx))
				queue.add(tx);
		}
	}
}
