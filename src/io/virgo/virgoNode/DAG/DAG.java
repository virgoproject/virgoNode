package io.virgo.virgoNode.DAG;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Timer;
import java.util.TimerTask;
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
import io.virgo.virgoCryptoLib.Utils;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Events.EventListener;
import io.virgo.virgoNode.DAG.Infos.DAGInfos;
import io.virgo.virgoNode.Data.TxLoader;
import io.virgo.virgoNode.Data.TxWriter;
import io.virgo.virgoNode.Utils.Miscellaneous;
import io.virgo.virgoNode.network.Peers;

/**
 * Represents transactions Directed Acyclic Graph data structure
 * Contains methods to add and fetch transactions
 */
public class DAG {

	private List<String> processingTransactions = Collections.synchronizedList(new ArrayList<String>());
	private ConcurrentHashMap<String, LoadedTransaction> loadedTransactions = new ConcurrentHashMap<String, LoadedTransaction>();	protected NavigableMap<Long, LoadedTransaction> nodesToCheck = Collections.synchronizedNavigableMap(new TreeMap<Long, LoadedTransaction>());
	ConcurrentHashMap<String, List<OrphanTransaction>> waitedTxs = new ConcurrentHashMap<String, List<OrphanTransaction>>();
	protected List<String> waitingTxsUids = Collections.synchronizedList(new ArrayList<String>());
	List<LoadedTransaction> childLessTxs = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	List<LoadedTransaction> childLessBeacons = Collections.synchronizedList(new ArrayList<LoadedTransaction>());

	private LoadedTransaction genesis;
	
	public TxWriter writer;
	public TxLoader loader;
	
	public int saveInterval;
	
	final private EventListener eventListener;
	
	final public DAGInfos infos = new DAGInfos();
	
	public DAG(int saveInterval) throws IOException {
		this.saveInterval = saveInterval;
	
		//start event listener thread
		eventListener = new EventListener(this);
		(new Thread(eventListener)).start();
		
		//Add genesis transaction to DAG
		TxOutput out = new TxOutput("V2N5tYdd1Cm1xqxQDsY15x9ED8kyAUvjbWv", (long) (100000 * Math.pow(10, Main.DECIMALS)), "", "");
		TxOutput[] genesisOutputs = {out};
		
		genesis = new LoadedTransaction(this, genesisOutputs);
		loadedTransactions.put(genesis.getUid(), genesis);
		childLessTxs.add(genesis);
		
		System.out.println("Genesis TxUid is " + genesis.getUid());
		
		//start transaction writer thread (writes transactions to disk)
		writer = new TxWriter(this);
		new Thread(writer).start();
		
		//start transaction loader thread (fetch transactions from disk)
		loader = new TxLoader(this);
		new Thread(loader).start();
		
		//load saved transactions
		loadDAG();
		
		//ask missing transactions and latest tips to peers every 10s
		new Timer().scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				
				Peers.getTips();
				
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {}
				
				if(waitedTxs.size() != 0) {
					Collection<String> lakingTransactions = waitedTxs.keySet();
					lakingTransactions.removeAll(waitingTxsUids);
					Peers.askTxs(lakingTransactions);
				}
			}
			
		}, 10000, 10000);
		
	}
	
	/**
	 * Load saved transactions
	 */
	private void loadDAG() {

		//get last know tips from database then try to load them, it will automatically try to load all transactions before those
		try {
			JSONArray tips = Main.getDatabase().getTips();
			
			if(tips.length() == 0) {
				System.out.println("No tips data");
				return;
			}
			
			System.out.println("Tips found, restoring DAG");
			
			for(int i = 0; i < tips.length(); i++) {
				String txHash = tips.getJSONObject(i).getString("id");
				
				if(Miscellaneous.validateAddress(txHash, Main.TX_IDENTIFIER))
					loader.push(txHash);
			}
			
		} catch (SQLException e) {
			System.out.println("Unable to retrieve tips from Database: " + e.getMessage());
		}
		
	}

	public synchronized LoadedTransaction getLoadedTx(String hash) {
		return loadedTransactions.get(hash);
	}
	
	
	public void initTx(JSONObject txJson, boolean saved) throws JSONException, IllegalArgumentException {

		JSONArray parents = txJson.getJSONArray("parents");
		JSONArray outputs = txJson.getJSONArray("outputs");
		long date = txJson.getLong("date");
		
		//transaction is a beacon transaction
		if(txJson.has("parentBeacon")) {
			
			String parentBeacon = txJson.getString("parentBeacon");
			long nonce = txJson.getLong("nonce");
		
			String txUid = Converter.Addressify(Sha256.getDoubleHash((parents.toString() + outputs.toString() + parentBeacon + date + nonce).getBytes()).toBytes(), Main.TX_IDENTIFIER);
			System.out.println("loading beacontx " + txUid);
			//Ensure transaction isn't processed yet
			if(loadedTransactions.containsKey(txUid) || waitingTxsUids.contains(txUid))
				return;
			
			//prevent concurrency with synchronized list of processing transactions
			synchronized(processingTransactions) {
				if(processingTransactions.contains(txUid))
					return;
				else
					processingTransactions.add(txUid);
			}
			
			initBeaconTx(parents, outputs, parentBeacon, nonce, date, saved);
			
		//regular transaction
		}else {
			byte[] sigBytes = Converter.hexToBytes(txJson.getString("sig"));
			byte[] pubKey = Converter.hexToBytes(txJson.getString("pubKey"));
			JSONArray inputs = txJson.getJSONArray("inputs");
			
			String txUid = Converter.Addressify(sigBytes, Main.TX_IDENTIFIER);
			System.out.println("loading tx " + txUid);
			//Ensure transaction isn't processed yet
			if(loadedTransactions.containsKey(txUid) || waitingTxsUids.contains(txUid))
				return;
			
			//prevent concurrency with synchronized list of processing transactions
			synchronized(processingTransactions) {
				if(processingTransactions.contains(txUid))
					return;
				else
					processingTransactions.add(txUid);
			}
			
			initTx(sigBytes, pubKey, parents, inputs, outputs, date, saved);
		}
			
	}
	
	public void initBeaconTx(JSONArray parents, JSONArray outputs, String parentBeacon, long nonce, long date, boolean saved) {
		
		Sha256Hash txHash = Sha256.getDoubleHash((parents.toString() + outputs.toString() + parentBeacon + date + nonce).getBytes());
		String txUid = Converter.Addressify(txHash.toBytes(), Main.TX_IDENTIFIER);
		
		JSONArray[] cleanedTx = cleanTx(parents, new JSONArray(), outputs);
		parents = cleanedTx[0];
		outputs = cleanedTx[2];
		
		//beacon transaction must have only one output
		if(outputs.length() != 1) {
			processingTransactions.remove(txUid);
			return;
		}
		
		if(!Utils.validateAddress(parentBeacon, Main.TX_IDENTIFIER) || parents.isEmpty()) {
			processingTransactions.remove(txUid);
			throw new IllegalArgumentException("Invalid transaction format");
		}
		
		//Convert parents JSON to string array
		ArrayList<String> parentsUids = new ArrayList<String>();
		for(int i = 0; i < parents.length(); i++)
			parentsUids.add(parents.getString(i));
		
		//Build TxOutput objects for this transaction
		ArrayList<TxOutput> constructedOutput = new ArrayList<TxOutput>();
		
		TxOutput output = TxOutput.fromString(outputs.getString(0), txUid);
		constructedOutput.add(output);
		
		if(output.getAmount() != Main.BEACON_REWARD) {
			processingTransactions.remove(txUid);
			return;
		}
		
		//Create corresponding Transaction object
		Transaction tx = new Transaction(txUid,
				parentsUids.toArray(new String[parentsUids.size()]),
				constructedOutput.toArray(new TxOutput[1]),
				parentBeacon, nonce, date, saved);
		
		initBeaconTx(tx);
		
	}
	
	public void initBeaconTx(Transaction tx) {
		
		ArrayList<String> waitedTxs = new ArrayList<String>();
		
		//Check for missing parents 
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(String parentTxUid : tx.getParentsUids()) {
			LoadedTransaction parentTx = getLoadedTx(parentTxUid);
			if(parentTx == null)
				waitedTxs.add(parentTxUid);
			else
				loadedParents.add(parentTx);
		}
		
		//check for missing parent beacon
		LoadedTransaction parentBeacon = getLoadedTx(tx.getParentBeaconUid());
		if(parentBeacon == null)
			waitedTxs.add(tx.getParentBeaconUid());
		
		//if there is any missing transaction (input or parent) try to load them and add this transaction to waiting txs
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new String[waitedTxs.size()])));
			loader.push(waitedTxs);
			processingTransactions.remove(tx.getUid());
			return;
		}
		
		//check if transaction has no useless parent
		if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0)))) {
			processingTransactions.remove(tx.getUid());
			return;
		}
		
		//check if transaction timestamp is superior to last 10 blocks median and inferior to current time + 15 minutes
		ArrayList<Long> timestamps = new ArrayList<Long>();
		LoadedTransaction currentParent = parentBeacon;
		for(int i = 0; i < 10; i++) {
			timestamps.add(currentParent.getDate());
			currentParent = currentParent.getParentBeacon();
			
			//for first 10 blocks
			if(currentParent == null)
				break;
		}
		
		timestamps.sort(null);
		
		long medianTime = timestamps.get(timestamps.size()/2);
		
		if(tx.getDate() < medianTime || tx.getDate() > System.currentTimeMillis()+900000) {
			processingTransactions.remove(tx.getUid());
			return;
		}
		
		//check if transaction is effectively child of it's parent beacon
		boolean childOf = false;
		for(LoadedTransaction parent : loadedParents)
			if(parent.isChildOf(parentBeacon)) {
				childOf = true;
				break;
			}
				
		if(!childOf) {
			processingTransactions.remove(tx.getUid());
			return;
		}
		
		JSONObject txJson = tx.toJSONObject();
		Sha256Hash txHash = Sha256.getDoubleHash((
				txJson.getJSONArray("parents").toString()
				+ txJson.getJSONArray("outputs").toString()
				+ parentBeacon.getUid()
				+ tx.getDate()
				+ tx.getNonce()
				).getBytes());
		
		//check if required difficulty is met
		if(txHash.toLong() >= Main.MAX_DIFFICULTY/parentBeacon.getDifficulty()) {
			processingTransactions.remove(tx.getUid());
			return;
		}
		
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(this, tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), parentBeacon);
		loadedTransactions.put(loadedTx.getUid(), loadedTx);
		
		System.out.println("saving1 " + tx.getUid());
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		//remove transaction from processing ones
		processingTransactions.remove(tx.getUid());
		
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getUid());		
		
	}
	
	/**
	 * Initiate regular transaction from raw data
	 * Includes checks for data and signature validity 
	 */
	public void initTx(byte[] sigBytes, byte[] pubKey, JSONArray parents, JSONArray inputs, JSONArray outputs, long date, boolean saved) throws IllegalArgumentException {
		
		String txUid = Converter.Addressify(sigBytes, Main.TX_IDENTIFIER);
		
		//Remove any useless data from transaction, if there is then transaction will be refused
		JSONArray[] cleanedTx = cleanTx(parents, inputs, outputs);
		parents = cleanedTx[0];
		inputs = cleanedTx[1];
		outputs = cleanedTx[2];
		
		//make sure neither inputs or ouputs are empty
		if(inputs.isEmpty() || outputs.isEmpty() || parents.isEmpty()) {
			processingTransactions.remove(txUid);
			throw new IllegalArgumentException("Invalid transaction format");
		}
		
		//check if signature is valid, bypass this check if transaction is coming from disk			
		if(!saved) {
		
			ECDSA signer = new ECDSA();
			ECDSASignature sig = ECDSASignature.fromByteArray(sigBytes);
			
			Sha256Hash txHash = Sha256.getDoubleHash((parents.toString() + inputs.toString() + outputs.toString() + date).getBytes());
			if(!signer.Verify(txHash, sig, pubKey)) {
				processingTransactions.remove(txUid);
				throw new IllegalArgumentException("Invalid signature");
			}
			
		}
		
		//Convert parents JSON to string array
		ArrayList<String> parentsUids = new ArrayList<String>();
		for(int i = 0; i < parents.length(); i++)
			parentsUids.add(parents.getString(i));
		
		//same with inputs
		ArrayList<String> inputsUids = new ArrayList<String>();
		for(int i = 0; i < inputs.length(); i++)
			inputsUids.add(inputs.getString(i));
		
		//Build TxOutput objects for this transaction
		ArrayList<TxOutput> constructedOutputs = new ArrayList<TxOutput>();
		
		for(int i = 0; i < outputs.length(); i++) {
			TxOutput output = TxOutput.fromString(outputs.getString(i), txUid);
			constructedOutputs.add(output);
		}
		
		//Create corresponding Transaction object
		Transaction tx = new Transaction(pubKey, ECDSASignature.fromByteArray(sigBytes),
				parentsUids.toArray(new String[parentsUids.size()]),
				inputsUids.toArray(new String[inputsUids.size()]),
				constructedOutputs.toArray(new TxOutput[constructedOutputs.size()]),
				date, saved);
		
		initTx(tx);
		
	}
	
	/**
	 * Initiate transaction from Transaction object
	 */
	public void initTx(Transaction tx) {
		ArrayList<String> waitedTxs = new ArrayList<String>();
		
		//Check for missing parents 
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(String parentTxUid : tx.getParentsUids()) {
			LoadedTransaction parentTx = getLoadedTx(parentTxUid);
			if(parentTx == null)
				waitedTxs.add(parentTxUid);
			else
				loadedParents.add(parentTx);
		}
		
		String[] inputsUids = tx.getInputsUids();
		
		//check for missing inputs
		ArrayList<LoadedTransaction> loadedInputs = new ArrayList<LoadedTransaction>();
		for(String inputTxUid : inputsUids) {
			LoadedTransaction inputTx = getLoadedTx(inputTxUid);
			if(inputTx == null)
				waitedTxs.add(inputTxUid);
			else
				loadedInputs.add(inputTx);
		}
		
		//if there is any missing transaction (input or parent) try to load them and add this transaction to waiting txs
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new String[waitedTxs.size()])));
			loader.push(waitedTxs);
			processingTransactions.remove(tx.getUid());
			return;
		}

		//check if transaction has no useless parent
		if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0)))) {
			processingTransactions.remove(tx.getUid());
			return;
		}

		//Make a list of all input we are child of
		ArrayList<LoadedTransaction> childOfInputs = new ArrayList<LoadedTransaction>();
		for(LoadedTransaction parent : loadedParents)
			for(LoadedTransaction input : loadedInputs)
			if(parent.isChildOf(input))
				childOfInputs.add(input);
			
		long totalInputValue = 0;
		
		//check if inputs are valid and calculate inputs total value
		for(LoadedTransaction input : loadedInputs) {
			//check if transaction is child of its input
			if(!childOfInputs.contains(input)) {
				processingTransactions.remove(tx.getUid());
				return;
			}
			
			if(!input.getOutputsMap().containsKey(tx.getAddress())) {
				processingTransactions.remove(tx.getUid());
				return;
			}
			
			if(input.getDate() > tx.getDate()) {
				processingTransactions.remove(tx.getUid());
				return;
			}
			
			long amount = input.getOutputsMap().get(tx.getAddress()).getAmount();
			totalInputValue += amount;
		}
		
		//Check if inputs contains enough funds to cover outputs
		if(totalInputValue != tx.getOutputsValue()) {
			processingTransactions.remove(tx.getUid());
			throw new IllegalArgumentException("Trying to spend more than allowed ("+tx.getOutputsValue()+" / " + totalInputValue +")");
		}
			
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(this, tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), loadedInputs.toArray(new LoadedTransaction[loadedInputs.size()]));
		
		loadedTransactions.put(loadedTx.getUid(), loadedTx);
		
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		//remove transaction from processing ones
		processingTransactions.remove(tx.getUid());
		
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getUid());		
		
	}
	
	/**
	 * Remove useless or invalid data from transaction
	 */
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
				TxOutput output = TxOutput.fromString(outputs.getString(i), "");				
				cleanedOutputs.put(output.toString());
			}catch(JSONException | ArithmeticException | IllegalArgumentException e) {}
		}
		
		return new JSONArray[] {cleanedParents, cleanedInputs, cleanedOutputs};
	}
	
	/**
	 * Register that orphanTx is waiting for txs so we try to load it once every txs are loaded
	 */
	public synchronized void addWaitedTxs(ArrayList<String> txs, OrphanTransaction orphanTx) {
		
		for(String tx : txs) {
			if(waitedTxs.containsKey(tx))
				waitedTxs.get(tx).add(orphanTx);
			else
				waitedTxs.put(tx, Collections.synchronizedList(new ArrayList<OrphanTransaction>(Arrays.asList(orphanTx))));
		}
		
		waitingTxsUids.add(orphanTx.getUid());
	}
	
	/**
	 * Check if any transaction is waiting for this one and remove it from waited list
	 */
	public void removeWaitedTx(String tx) {
		if(!waitedTxs.containsKey(tx))
			return;
		
		for(OrphanTransaction orphanTx : waitedTxs.get(tx))
			orphanTx.removeWaitedTx(tx);
		
		waitedTxs.remove(tx);
	}
	
	/**
	 * Get best tips ids to use as parent for a new transaction
	 */
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

	public LoadedTransaction getBestTipBeacon() {
		LoadedTransaction selectedBeacon = null;
		
		for(LoadedTransaction beacon : childLessBeacons) {
			if(selectedBeacon == null) {
				selectedBeacon = beacon;
				continue;
			}
			
			if(selectedBeacon.getWeight() < beacon.getWeight()) {
				selectedBeacon = beacon;
				continue;
			}
			
			if(selectedBeacon.getWeight() == beacon.getWeight()) {
				if(selectedBeacon.getBeaconHeight() < beacon.getBeaconHeight()) {
					selectedBeacon = beacon;
					continue;
				}
				
				if(selectedBeacon.getBeaconHeight() == beacon.getBeaconHeight()) {
					if(selectedBeacon.getDate() > beacon.getDate()) {
						selectedBeacon = beacon;
						continue;
					}
				}
			}
		}
		
		return selectedBeacon;
	}
	
	/**
	 * @param uid A transaction id
	 * @return true if the transaction is loaded or is present in database, false otherwise
	 */
	public boolean hasTransaction(String uid) {
		if(loadedTransactions.containsKey(uid) || waitingTxsUids.contains(uid))
			return true;
		
		try {
			return Main.getDatabase().getTx(uid) != null;
		} catch (SQLException e) {
			System.out.println("hasTransaction SQL error: " + e.getMessage());
		}
		
		return false;
	}

	/**
	 * @param uid A transaction ID
	 * @return The JSON representation of this transaction
	 */
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
	
	/**
	 * 
	 * @param txUid A transaction ID
	 * @return true if this transaction is waiting for other to load
	 */
	public boolean isTxWaiting(String txUid) {
		return waitingTxsUids.contains(txUid);
	}
	
	public EventListener getEventListener() {
		return eventListener;
	}
	
	/**
	 * @param txUid A transaction ID
	 * @return true if transaction is loaded, false otherwise
	 */
	public boolean isLoaded(String txUid) {
		return loadedTransactions.containsKey(txUid);
	}

	public long loadedTxsCount() {
		return loadedTransactions.size();
	}
	
	public long getPoolSize() {
		// TODO Auto-generated method stub
		return waitingTxsUids.size();
	}

	public LoadedTransaction getGenesis() {
		return genesis;
	}
	
	public static long calcDifficulty(long lastDiff, long solveTime, long blockNumber) {
		
		return lastDiff + lastDiff / 2048 * Math.max(1 - solveTime / 60, -99) + (long)(Math.pow(2d, (blockNumber / 100) - 2));
		
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
