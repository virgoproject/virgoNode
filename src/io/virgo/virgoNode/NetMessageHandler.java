package io.virgo.virgoNode;

import java.util.ArrayList;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.TxOutput;
import io.virgo.virgoNode.DAG.Infos.AddressInfos;
import io.virgo.geoWeb.MessageHandler;
import io.virgo.geoWeb.Peer;

/**
 * Class handling peers messages, like new transactions or transaction requests
 * TODO: Refactor into a function oriented event handler
 *
 */
public class NetMessageHandler extends MessageHandler {

	public HashSet<String> askedTxs = new HashSet<String>();
	
	@Override
	public void onMessage(JSONObject messageJson, Peer peer) {
		try {
			
			switch(messageJson.getString("command")) {
				case "askTx":
					String txId = messageJson.getString("id");
					
					if(!Main.getDAG().hasTransaction(txId))
						return;
					
					JSONObject response = new JSONObject();	
					response.put("command", "inv");
					response.put("id", txId);
					peer.respondToMessage(response, messageJson);
					
					break;
					
				case "inv":
					String id = messageJson.getString("id");
					
					if(Main.getDAG().hasTransaction(id))
						return;
					
					JSONObject invResp = new JSONObject();	
					invResp.put("command", "getTx");
					invResp.put("id", id);
					peer.respondToMessage(invResp, messageJson);
					
					askedTxs.add(id);
					
					break;
					
				case "getTx":
					String askedId = messageJson.getString("id");
					System.out.println(askedId);
					JSONObject txJSON = Main.getDAG().getTxJSON(askedId);
					
					if(txJSON == null)
						return;
					System.out.println("qdzzd");
					JSONObject getTxResp = new JSONObject();	
					getTxResp.put("command", "tx");
					getTxResp.put("tx", txJSON);
					peer.respondToMessage(getTxResp, messageJson);
					
					break;
					
				case "tx":
					
					JSONObject txJson = messageJson.getJSONObject("tx");
					String txUid = Converter.Addressify(Converter.hexToBytes(txJson.getString("sig")), Main.TX_IDENTIFIER);
					
					if(Main.getDAG().hasTransaction(txUid))
						return;
					
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
						
						//transmit tx to other peers
						JSONObject txInv = new JSONObject();	
						txInv.put("command", "inv");
						txInv.put("id", txUid);
						ArrayList<Peer> peerToExclude = new ArrayList<Peer>();
						peerToExclude.add(peer);
						Main.getGeoWeb().broadCast(txInv, peerToExclude);
					}catch(IllegalArgumentException e) {
						if(messageJson.has("callback")) {
							JSONObject txCallback = new JSONObject();	
							txCallback.put("command", "txCallback");
							txCallback.put("id", txUid);
							txCallback.put("result", false);
							peer.respondToMessage(txCallback, messageJson);
						}
						System.out.println(e.getMessage());
						return;
					}
					
					askedTxs.remove(txUid);
					break;
					
				case "getTips":
					JSONObject tipsMsg = new JSONObject();	
					tipsMsg.put("command", "tips");
					tipsMsg.put("tips", new JSONArray(Main.getDAG().getTipsUids()));
					peer.respondToMessage(tipsMsg, messageJson);
					break;
					
				case "tips":
					JSONArray tips = messageJson.getJSONArray("tips");
					for(int i = 0; i < tips.length(); i++) {
						String tx = tips.getString(i);
						if(!Main.getDAG().hasTransaction(tx)) {
							JSONObject askTxMsg = new JSONObject();
							askTxMsg.put("command", "askTx");
							askTxMsg.put("id", tx);
							Main.getGeoWeb().broadCast(askTxMsg);
						}
					}
					break;
					
				case "getBalances":
					JSONObject balancesResp = new JSONObject();
					balancesResp.put("command", "balances");
					
					JSONArray balances = new JSONArray();
					
					JSONArray askedBalances = messageJson.getJSONArray("addresses");
					for(int i = 0; i < askedBalances.length(); i++) {
						String address = askedBalances.getString(i);
						
						JSONObject balance = new JSONObject();
						balance.put("address", address);
						
						if(Main.getDAG().infos.hasAddressInfos(address)) {
							AddressInfos addrInfos = Main.getDAG().infos.getAddressInfos(address);
							balance.put("received", addrInfos.getTotalReceived());
							balance.put("sent", addrInfos.getTotalSent());
						}else {
							balance.put("received", 0);
							balance.put("sent", 0);
						}
						
						balances.put(balance);
					}
					
					balancesResp.put("balances", balances);
					
					peer.respondToMessage(balancesResp, messageJson);
					break;
					
				case "getAddrTxs":
					JSONObject addrTxsResp = new JSONObject();
					addrTxsResp.put("command", "addrTxs");
					
					JSONArray addrTxs = new JSONArray();
					
					JSONArray addresses = messageJson.getJSONArray("addresses");
					for(int i = 0; i < addresses.length(); i++) {
						String address = addresses.getString(i);
						
						JSONObject txs = new JSONObject();
						txs.put("address", address);
						
						if(Main.getDAG().infos.hasAddressInfos(address)) {
							AddressInfos addrInfos = Main.getDAG().infos.getAddressInfos(address);
							
							txs.put("inputs", new JSONArray(addrInfos.getInputTxs()));
							txs.put("outputs", new JSONArray(addrInfos.getOutputTxs()));
						} else {
							txs.put("inputs", new JSONArray());
							txs.put("outputs", new JSONArray());
						}
						
						addrTxs.put(txs);
					}
					
					addrTxsResp.put("addrTxs", addrTxs);
					
					peer.respondToMessage(addrTxsResp, messageJson);
					break;
					
				case "getTxsState":
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
						}else {
							txState.put("notLoaded", true);
						}
						
						txsState.put(txState);
					}
					
					txsStateResp.put("txsState", txsState);
					
					peer.respondToMessage(txsStateResp, messageJson);
					break;
					
				case "getNodeInfos":
					System.out.println("sent infos");
					
					JSONObject nodeInfosResp = new JSONObject();
					nodeInfosResp.put("command", "nodeInfos");
					nodeInfosResp.put("appName", Main.getAppName());
					nodeInfosResp.put("netID", Main.getNetID());
					nodeInfosResp.put("DAGHeight", Main.getDAG().getMainChainLength());
					nodeInfosResp.put("DAGWeight", Main.getDAG().infos.getTransactionCount());
					nodeInfosResp.put("poolSize", Main.getDAG().getPoolSize());
					
					peer.respondToMessage(nodeInfosResp, messageJson);
			}
			
		}catch(JSONException e) {
			System.out.println(e.getMessage());
		}
	}
	
}
