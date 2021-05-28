package io.virgo.virgoNode.network;

import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.geoWeb.MessageHandler;
import io.virgo.geoWeb.Peer;

/**
 * Class handling peers messages, like new transactions or transaction requests
 * TODO: Refactor into a function oriented event handler
 *
 */
public class NetMessageHandler extends MessageHandler {


	@Override
	public void onMessage(JSONObject messageJson, Peer peer) {
		try {
			
			switch(messageJson.getString("command")) {
				case "askTxs":
					OnAskTxs.handle(messageJson, peer);
					break;
					
				case "inv":
					OnInv.handle(messageJson, peer);
					break;
					
				case "getTxs":
					OnGetTxs.handle(messageJson, peer);
					break;
					
				case "txs":
					OnTxs.handle(messageJson, peer);
					break;
					
				case "getTips":
					OnGetTips.handle(messageJson, peer);
					break;
					
				case "tips":
					OnTips.handle(messageJson, peer);
					break;
					
				case "getBalances":
					OnGetBalances.handle(messageJson, peer);
					break;
					
				case "getAddrTxs":
					OnGetAddrTxs.handle(messageJson, peer);
					break;
					
				case "getTxsState":
					OnGetTxsState.handle(messageJson, peer);
					break;
					
				case "getNodeInfos":
					OnGetNodeInfos.handle(messageJson, peer);
					break;
					
				case "getPoWInformations":
					OnGetPoWInformations.handle(messageJson, peer);
			}
			
		}catch(JSONException|IllegalArgumentException e) {}
	}
	
}
