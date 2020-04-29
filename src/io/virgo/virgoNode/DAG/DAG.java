package io.virgo.virgoNode.DAG;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.ECDSA;
import io.virgo.virgoCryptoLib.ECDSASignature;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Events.EventListener;
import io.virgo.virgoNode.DAG.Infos.DAGInfos;
import io.virgo.virgoNode.Data.TxLoader;
import io.virgo.virgoNode.Data.TxWriter;
import io.virgo.virgoNode.Utils.Miscellaneous;

public class DAG {

	private ConcurrentHashMap<String, LoadedTransaction> loadedTransactions = new ConcurrentHashMap<String, LoadedTransaction>();
	protected List<LoadedTransaction> mainChain = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	protected NavigableMap<Long, LoadedTransaction> nodesToCheck = Collections.synchronizedNavigableMap(new TreeMap<Long, LoadedTransaction>());
	ConcurrentHashMap<String, List<OrphanTransaction>> waitedTxs = new ConcurrentHashMap<String, List<OrphanTransaction>>();
	protected List<String> waitingTxsUids = Collections.synchronizedList(new ArrayList<String>());
	private List<LoadedTransaction> childLessTxs = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	
	public TxWriter writer;
	public TxLoader loader;
	
	public int saveInterval;
	
	final private EventListener eventListener;
	
	final public DAGInfos infos = new DAGInfos();
	
	public DAG(int saveInterval) throws IOException {
		this.saveInterval = saveInterval;
		
		eventListener = new EventListener(this);
		(new Thread(eventListener)).start();
		
		TxOutput out = new TxOutput("V2N5tYdd1Cm1xqxQDsY15x9ED8kyAUvjbWv", Main.TOTALUNITS, "", "");
		out.usable = true;
		
		TxOutput[] genesisOutputs = {out};
		
		LoadedTransaction genesis = new LoadedTransaction(this, genesisOutputs);
		loadedTransactions.put(genesis.getUid(), genesis);
		childLessTxs.add(genesis);
		
		System.out.println("Genesis TxUid is " + genesis.getUid());
		
		writer = new TxWriter(this);
		new Thread(writer).start();
		
		loader = new TxLoader(this);
		new Thread(loader).start();
		
		loadDAG();
		
		JSONObject getTipsMsg = new JSONObject();
		getTipsMsg.put("command", "getTips");
		Main.getGeoWeb().broadCast(getTipsMsg);
		
	}
	
	private void loadDAG() {

		try {
			JSONArray tips = Main.getDatabase().getTips();
			
			if(tips.length() == 0) {
				System.out.println("No tips data");
				return;
			}
			
			System.out.println("Tips found, restoring DAG");
			
			String txHash = "";
			
			for(int i = 0; i < tips.length(); i++) {
				txHash = tips.getJSONObject(i).getString("id");
				if(Miscellaneous.validateAddress(txHash, Main.TX_IDENTIFIER)) {
					System.out.println("pushing " + txHash);
					loader.push(txHash);
				}
					
			}
			
		} catch (SQLException e) {
			System.out.println("Unable to retrieve tips from Database: " + e.getMessage());
		}
		
	}

	public synchronized LoadedTransaction getLoadedTx(String hash) {
		return loadedTransactions.get(hash);
	}
	
	public void initTx(byte[] sigBytes, byte[] pubKey, JSONArray parents, JSONArray inputs, JSONArray outputs, long date, boolean saved) throws IllegalArgumentException {
		
		String txUid = Converter.Addressify(sigBytes, Main.TX_IDENTIFIER);
		String address = Converter.Addressify(pubKey, Main.ADDR_IDENTIFIER);
		
		if(loadedTransactions.containsKey(txUid) || waitingTxsUids.contains(txUid))
			return;
		
		JSONArray[] cleanedTx = cleanTx(parents, inputs, outputs);
		parents = cleanedTx[0];
		inputs = cleanedTx[1];
		outputs = cleanedTx[2];
		
		//make sure neither inputs or ouputs are empty
		if(inputs.isEmpty() || outputs.isEmpty() || parents.isEmpty())
			throw new IllegalArgumentException("Invalid transaction format");
		
		ECDSA signer = new ECDSA();
		ECDSASignature sig = ECDSASignature.fromByteArray(sigBytes);
		
		//check if signature is good
		Sha256Hash TxHash = Sha256.getHash((parents.toString() + inputs.toString() + outputs.toString()).getBytes());
		if(!signer.Verify(TxHash, sig, pubKey))
			throw new IllegalArgumentException("Invalid signature");
		
		ArrayList<String> parentsUids = new ArrayList<String>();
		for(int i = 0; i < parents.length(); i++)
			parentsUids.add(parents.getString(i));
		
		ArrayList<String> inputsUids = new ArrayList<String>();
		for(int i = 0; i < inputs.length(); i++)
			inputsUids.add(inputs.getString(i));
		
		ArrayList<TxOutput> constructedOutputs = new ArrayList<TxOutput>();
		
		for(int i = 0; i < outputs.length(); i++) {
			TxOutput output = TxOutput.fromString(outputs.getString(i), txUid, address);
			constructedOutputs.add(output);
		}
		
		Transaction tx = new Transaction(pubKey, ECDSASignature.fromByteArray(sigBytes),
				parentsUids.toArray(new String[parentsUids.size()]),
				inputsUids.toArray(new String[inputsUids.size()]),
				constructedOutputs.toArray(new TxOutput[constructedOutputs.size()]),
				date, saved);
		
		initTx(tx);
		
	}
	
	public void initTx(Transaction tx) {
		ArrayList<String> waitedTxs = new ArrayList<String>();
		
		String[] parentsUids = tx.getParentsUids();
		
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(String parentTxUid : parentsUids) {
			LoadedTransaction parentTx = getLoadedTx(parentTxUid);
			if(parentTx == null)
				waitedTxs.add(parentTxUid);
			else
				loadedParents.add(parentTx);
		}
		
		String[] inputsUids = tx.getInputsUids();
		
		ArrayList<LoadedTransaction> loadedInputs = new ArrayList<LoadedTransaction>();
		for(String inputTxUid : inputsUids) {
			LoadedTransaction inputTx = getLoadedTx(inputTxUid);
			if(inputTx == null)
				waitedTxs.add(inputTxUid);
			else
				loadedInputs.add(inputTx);
		}
		
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new String[waitedTxs.size()])));
			loader.push(waitedTxs);
			return;
		}
		
		long totalInputValue = 0;
		
		//check if inputs are valid and calculate inputs total value
		for(LoadedTransaction input : loadedInputs) {
			if(!input.getOutputsMap().containsKey(tx.getAddress()))
				return;
			
			if(input.getDate() > tx.getDate())
				return;
			
			long amount = input.getOutputsMap().get(tx.getAddress()).getAmount();
			totalInputValue += amount;
		}
		
		for(LoadedTransaction parent : loadedParents)
			if(parent.getDate() > tx.getDate())
				return;
		
		if(totalInputValue < tx.getOutputsValue())
			throw new IllegalArgumentException("Trying to spend more than allowed ("+tx.getOutputsValue()+" / " + totalInputValue +")");
		
		LoadedTransaction loadedTx = new LoadedTransaction(this, tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), loadedInputs.toArray(new LoadedTransaction[loadedInputs.size()]));
		
		loadedTransactions.put(loadedTx.getUid(), loadedTx);
		childLessTxs.add(loadedTx);
		for(LoadedTransaction parent : loadedTx.getLoadedParents())
				childLessTxs.remove(parent);
		
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		removeWaitedTx(loadedTx.getUid());		
		
	}
	
	public static JSONArray[] cleanTx(JSONArray parents, JSONArray inputs, JSONArray outputs) {
		
		JSONArray cleanedParents = new JSONArray();
		
		for(int i = 0; i < parents.length(); i++) {
			try {
				String parent = parents.getString(i);
				if(Miscellaneous.validateAddress(parent, Main.TX_IDENTIFIER))
					cleanedParents.put(parent);

			}catch(JSONException e) {}
		}
		
		JSONArray cleanedInputs = new JSONArray();
		
		for(int i = 0; i < inputs.length(); i++) {
			try {
				String input = inputs.getString(i);
				if(Miscellaneous.validateAddress(input, Main.TX_IDENTIFIER))
					cleanedInputs.put(input);
			}catch(JSONException e) {}
		}
		
		JSONArray cleanedOutputs = new JSONArray();
		
		for(int i = 0; i < outputs.length(); i++) {
			try {
				TxOutput output = TxOutput.fromString(outputs.getString(i), "", "");				
				cleanedOutputs.put(output.toString());
			}catch(JSONException | ArithmeticException | IllegalArgumentException e) {}
		}
		
		return new JSONArray[] {cleanedParents, cleanedInputs, cleanedOutputs};
	}
	
	public synchronized void addWaitedTxs(ArrayList<String> txs, OrphanTransaction orphanTx) {
		
		for(String tx : txs) {
			if(waitedTxs.containsKey(tx))
				waitedTxs.get(tx).add(orphanTx);
			else
				waitedTxs.put(tx, Collections.synchronizedList(new ArrayList<OrphanTransaction>(Arrays.asList(orphanTx))));
		}
		
		waitingTxsUids.add(orphanTx.getUid());
	}
	
	public void removeWaitedTx(String tx) {
		if(!waitedTxs.containsKey(tx))
			return;
		
		for(OrphanTransaction orphanTx : waitedTxs.get(tx))
			orphanTx.removeWaitedTx(tx);
		
		waitedTxs.remove(tx);
	}
	
	public ArrayList<String> getBestParents() {
		ArrayList<String> bestParents = new ArrayList<String>();
		while(bestParents.size() == 0) {//avoid desync probs, there can't be 0 childLess txs
			if(childLessTxs.size() == 1) {
				bestParents.add(childLessTxs.get(0).getUid());
			}else if(childLessTxs.size() >= 2) {
				bestParents.add(childLessTxs.get(0).getUid());
				bestParents.add(childLessTxs.get(1).getUid());
			}
		}
		
		return bestParents;
	}

	public boolean hasTransaction(String uid) {
		if(loadedTransactions.containsKey(uid))
			return true;
		
		try {
			return Main.getDatabase().getTx(uid) != null;
		} catch (SQLException e) {
			System.out.println("hasTransaction SQL error: " + e.getMessage());
		}
		
		return false;
	}

	public JSONObject getTxJSON(String uid) {
		if(loadedTransactions.containsKey(uid)) {
			return loadedTransactions.get(uid).toJSONObject();
		}
		
		try {
			return Main.getDatabase().getTx(uid);
		} catch (SQLException e) {
			System.out.println("getTxJSON SQL error: " + e.getMessage());
		}
		
		return null;
	}
	
	public boolean isTxWaiting(String txUid) {
		return waitingTxsUids.contains(txUid);
	}
	
	public EventListener getEventListener() {
		return eventListener;
	}
	
	public boolean isLoaded(String txUid) {
		return loadedTransactions.containsKey(txUid);
	}

	public long getMainChainLength() {
		return mainChain.size();
	}

	public long loadedTxsCount() {
		return loadedTransactions.size();
	}
	
	public long getPoolSize() {
		// TODO Auto-generated method stub
		return waitingTxsUids.size();
	}

	public ArrayList<String> getTipsUids() {
		ArrayList<String> uids = new ArrayList<String>();
		
		for(LoadedTransaction tip : childLessTxs) {
			uids.add(tip.getUid());
		}
		
		return uids;
	}
	
	public LoadedTransaction[] getTips() {
		return childLessTxs.toArray(new LoadedTransaction[childLessTxs.size()]);
	}
	
}
