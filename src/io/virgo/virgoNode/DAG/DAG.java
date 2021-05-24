package io.virgo.virgoNode.DAG;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.randomX.RandomX;
import io.virgo.randomX.RandomX_VM;
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
import io.virgo.virgoNode.network.Peers;

/**
 * Represents transactions Directed Acyclic Graph data structure
 * Contains methods to add and fetch transactions
 */
public class DAG {

	private List<Sha256Hash> processingTransactions = Collections.synchronizedList(new ArrayList<Sha256Hash>());
	private ConcurrentHashMap<Sha256Hash, LoadedTransaction> loadedTransactions = new ConcurrentHashMap<Sha256Hash, LoadedTransaction>();
	ConcurrentHashMap<Sha256Hash, List<OrphanTransaction>> waitedTxs = new ConcurrentHashMap<Sha256Hash, List<OrphanTransaction>>();
	protected List<Sha256Hash> waitingTxsHashes = Collections.synchronizedList(new ArrayList<Sha256Hash>());
	List<LoadedTransaction> childLessTxs = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	List<LoadedTransaction> childLessBeacons = Collections.synchronizedList(new ArrayList<LoadedTransaction>());

	private LoadedTransaction genesis;
	
	public TxWriter writer;
	public TxLoader loader;
	
	public int saveInterval;
	
	final private EventListener eventListener;
	
	final public DAGInfos infos = new DAGInfos();
	
	private String currentVmKey;
	private RandomX randomX;
	private RandomX_VM randomX_vm;
	
	ThreadPoolExecutor transactionExecutorPool;
	
	public DAG(int saveInterval) throws IOException {
		this.saveInterval = saveInterval;
		
		//start event listener thread
		eventListener = new EventListener(this);
		(new Thread(eventListener)).start();
		
		//Add genesis transaction to DAG
		TxOutput out = new TxOutput("V2N5tYdd1Cm1xqxQDsY15x9ED8kyAUvjbWv", (long) (100000 * Math.pow(10, Main.DECIMALS)), new Sha256Hash("025a6f04e7047b713aaba7fc5003c8266302918c25d1526507becad795b01f3a"));
		TxOutput[] genesisOutputs = {out};
		
		genesis = new LoadedTransaction(this, genesisOutputs);
		loadedTransactions.put(genesis.getHash(), genesis);
		childLessTxs.add(genesis);
		
		randomX = new RandomX.Builder().build();
		
		randomX.init(genesis.getRandomXKey().getBytes());
		randomX_vm = randomX.createVM();
		
		currentVmKey = genesis.getRandomXKey();
		
		System.out.println("Genesis TxUid is " + genesis.getHash().toString());
				
		transactionExecutorPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);
		transactionExecutorPool.setKeepAliveTime(600000, TimeUnit.MILLISECONDS);

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
					Collection<Sha256Hash> lakingTransactions = waitedTxs.keySet();
					lakingTransactions.removeAll(waitingTxsHashes);
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
				Sha256Hash txHash = new Sha256Hash(tips.getJSONObject(i).getString("id"));
				
				loader.push(txHash);
			}
			
		} catch (SQLException e) {
			System.out.println("Unable to retrieve tips from Database: " + e.getMessage());
		}
		
	}

	public synchronized LoadedTransaction getLoadedTx(Sha256Hash hash) {
		return loadedTransactions.get(hash);
	}
	
	
	public void initTx(JSONObject txJson, boolean saved) throws JSONException, IllegalArgumentException {
		if(txJson.toString().length() > 2048)//java char takes 2 bytes, limit transaction size to 4kb
			return;
		
		JSONArray parents = txJson.getJSONArray("parents");
		JSONArray outputs = txJson.getJSONArray("outputs");
		long date = txJson.getLong("date");
		
		//transaction is a beacon transaction
		if(txJson.has("parentBeacon")) {
			
			String parentBeacon = txJson.getString("parentBeacon");
			String nonce = txJson.getString("nonce");
		
			Sha256Hash txHash = Sha256.getDoubleHash((parents.toString() + outputs.toString() + parentBeacon + date + nonce).getBytes());
			
			//Ensure transaction isn't processed yet
			if(loadedTransactions.containsKey(txHash) || waitingTxsHashes.contains(txHash))
				return;
			
			//prevent concurrency with synchronized list of processing transactions
			synchronized(processingTransactions) {
				if(processingTransactions.contains(txHash))
					return;
				else
					processingTransactions.add(txHash);
			}
			initBeaconTx(txHash, parents, outputs, parentBeacon, nonce, date, saved);
			
		//regular transaction
		}else {
			byte[] sigBytes = Converter.hexToBytes(txJson.getString("sig"));
			byte[] pubKey = Converter.hexToBytes(txJson.getString("pubKey"));
			JSONArray inputs = txJson.getJSONArray("inputs");
			
			Sha256Hash txHash = Sha256.getDoubleHash(Converter.concatByteArrays(
					(parents.toString() + inputs.toString() + outputs.toString()).getBytes(), pubKey, Miscellaneous.longToBytes(date)));
			
			//Ensure transaction isn't processed yet
			if(loadedTransactions.containsKey(txHash) || waitingTxsHashes.contains(txHash))
				return;

			//prevent concurrency with synchronized list of processing transactions
			synchronized(processingTransactions) {
				if(processingTransactions.contains(txHash))
					return;
				else
					processingTransactions.add(txHash);
			}
			
			initTx(txHash, sigBytes, pubKey, parents, inputs, outputs, date, saved);
		}
			
	}
	
	public void initBeaconTx(Sha256Hash txHash, JSONArray parents, JSONArray outputs, String parentBeacon, String nonce, long date, boolean saved) {
				
		CleanedTx cleanedTx = cleanTx(parents, new JSONArray(), outputs, parentBeacon);
		ArrayList<Sha256Hash> parentsHashes = cleanedTx.parents;
		ArrayList<TxOutput> constructedOutput = cleanedTx.outputs;
		Sha256Hash parentBeaconHash = cleanedTx.parentBeaconHash;
		
		if(parentsHashes.isEmpty()) {
			processingTransactions.remove(txHash);
			throw new IllegalArgumentException("Invalid transaction format");
		}
		
		//beacon transaction must have only one output
		if(constructedOutput.size() != 1) {
			processingTransactions.remove(txHash);
			return;
		}
		
		if(parentBeaconHash == null) {
			processingTransactions.remove(txHash);
			return;
		}
		
		if(constructedOutput.get(0).getAmount() != Main.BEACON_REWARD) {
			processingTransactions.remove(txHash);
			return;
		}
		//Create corresponding Transaction object
		Transaction tx = new Transaction(txHash,
				parentsHashes.toArray(new Sha256Hash[parentsHashes.size()]),
				constructedOutput.toArray(new TxOutput[1]),
				parentBeaconHash, nonce, date, saved);
		
		transactionExecutorPool.submit(new addTxTask(tx));
	}
	
	void initBeaconTx(Transaction tx) {
		ArrayList<Sha256Hash> waitedTxs = new ArrayList<Sha256Hash>();
		
		//Check for missing parents 
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(Sha256Hash parentTxUid : tx.getParentsHashes()) {
			LoadedTransaction parentTx = getLoadedTx(parentTxUid);
			if(parentTx == null)
				waitedTxs.add(parentTxUid);
			else
				loadedParents.add(parentTx);
		}
		
		//check for missing parent beacon
		LoadedTransaction parentBeacon = getLoadedTx(tx.getParentBeaconHash());
		if(parentBeacon == null)
			waitedTxs.add(tx.getParentBeaconHash());
		
		//if there is any missing transaction (input or parent) try to load them and add this transaction to waiting txs
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new Sha256Hash[waitedTxs.size()])));
			loader.push(waitedTxs);
			processingTransactions.remove(tx.getHash());
			return;
		}
		//check if transaction has no useless parent
		if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0)))) {
			processingTransactions.remove(tx.getHash());
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
			processingTransactions.remove(tx.getHash());
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
			processingTransactions.remove(tx.getHash());
			return;
		}
		if(!parentBeacon.getRandomXKey().equals(currentVmKey)) {
			randomX.changeKey(parentBeacon.getRandomXKey().getBytes());
			currentVmKey = genesis.getRandomXKey();
		}
		
		JSONObject txJson = tx.toJSONObject();
		byte[] txHash = randomX_vm.getHash((
				txJson.getJSONArray("parents").toString()
				+ txJson.getJSONArray("outputs").toString()
				+ parentBeacon.getHash().toString()
				+ tx.getDate()
				+ tx.getNonce()
				).getBytes());
		
		byte[] hashPadded = new byte[txHash.length + 1];
		for (int i = 0; i < txHash.length; i++) {
			hashPadded[i + 1] = txHash[i];
		}
		
		BigInteger hashValue = new BigInteger(ByteBuffer.wrap(hashPadded).array());
		
		//check if required difficulty is met
		if(hashValue.compareTo(Main.MAX_DIFFICULTY.divide(parentBeacon.getDifficulty())) >= 0) {
			processingTransactions.remove(tx.getHash());
			return;
		}
		
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(this, tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), parentBeacon);
		loadedTransactions.put(loadedTx.getHash(), loadedTx);
		
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		//remove transaction from processing ones
		processingTransactions.remove(tx.getHash());
		
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getHash());		
	}
	
	/**
	 * Initiate regular transaction from raw data
	 * Includes checks for data and signature validity 
	 */
	public void initTx(Sha256Hash txHash, byte[] sigBytes, byte[] pubKey, JSONArray parents, JSONArray inputs, JSONArray outputs, long date, boolean saved) throws IllegalArgumentException {
		
		//Remove any useless data from transaction, if there is then transaction will be refused
		CleanedTx cleanedTx = cleanTx(parents, inputs, outputs, null);
		ArrayList<Sha256Hash> parentsHashes = cleanedTx.parents;
		ArrayList<Sha256Hash> inputsHashes = cleanedTx.inputs;
		ArrayList<TxOutput> constructedOutputs = cleanedTx.outputs;
		
		//make sure neither inputs or ouputs are empty
		if(inputsHashes.isEmpty() || constructedOutputs.isEmpty() || parentsHashes.isEmpty()) {
			processingTransactions.remove(txHash);
			throw new IllegalArgumentException("Invalid transaction format");
		}
		
		//check if signature is valid, bypass this check if transaction is coming from disk			
		if(!saved) {
		
			ECDSA signer = new ECDSA();
			ECDSASignature sig = ECDSASignature.fromByteArray(sigBytes);
			
			if(!signer.Verify(txHash, sig, pubKey)) {
				processingTransactions.remove(txHash);
				throw new IllegalArgumentException("Invalid signature");
			}
			
		}
		
		//Create corresponding Transaction object
		Transaction tx = new Transaction(txHash, pubKey, ECDSASignature.fromByteArray(sigBytes),
				parentsHashes.toArray(new Sha256Hash[parentsHashes.size()]),
				inputsHashes.toArray(new Sha256Hash[inputsHashes.size()]),
				constructedOutputs.toArray(new TxOutput[constructedOutputs.size()]),
				date, saved);
		
		transactionExecutorPool.submit(new addTxTask(tx));
	}
	
	/**
	 * Initiate transaction from Transaction object
	 */
	void initTx(Transaction tx) {

		ArrayList<Sha256Hash> waitedTxs = new ArrayList<Sha256Hash>();
		
		//Check for missing parents 
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(Sha256Hash parentTxHash : tx.getParentsHashes()) {
			LoadedTransaction parentTx = getLoadedTx(parentTxHash);
			if(parentTx == null)
				waitedTxs.add(parentTxHash);
			else
				loadedParents.add(parentTx);
		}
		
		//check for missing inputs
		ArrayList<LoadedTransaction> loadedInputs = new ArrayList<LoadedTransaction>();
		for(Sha256Hash inputTxHash : tx.getInputsHashes()) {
			LoadedTransaction inputTx = getLoadedTx(inputTxHash);
			if(inputTx == null)
				waitedTxs.add(inputTxHash);
			else
				loadedInputs.add(inputTx);
		}
		
		//if there is any missing transaction (input or parent) try to load them and add this transaction to waiting txs
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new Sha256Hash[waitedTxs.size()])));
			loader.push(waitedTxs);
			processingTransactions.remove(tx.getHash());
			return;
		}

		//check if transaction has no useless parent
		if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0)))) {
			processingTransactions.remove(tx.getHash());
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
				processingTransactions.remove(tx.getHash());
				return;
			}
			
			if(!input.getOutputsMap().containsKey(tx.getAddress())) {
				processingTransactions.remove(tx.getHash());
				return;
			}
			
			if(input.getDate() > tx.getDate()) {
				processingTransactions.remove(tx.getHash());
				return;
			}
			
			long amount = input.getOutputsMap().get(tx.getAddress()).getAmount();
			totalInputValue += amount;
		}

		//Check if inputs contains enough funds to cover outputs
		if(totalInputValue != tx.getOutputsValue()) {
			processingTransactions.remove(tx.getHash());
			throw new IllegalArgumentException("Trying to spend more than allowed ("+tx.getOutputsValue()+" / " + totalInputValue +")");
		}
			
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(this, tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), loadedInputs.toArray(new LoadedTransaction[loadedInputs.size()]));
		
		loadedTransactions.put(loadedTx.getHash(), loadedTx);
		
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		//remove transaction from processing ones
		processingTransactions.remove(tx.getHash());
		
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getHash());
	}
	
	/**
	 * Remove useless or invalid data from transaction
	 */
	public CleanedTx cleanTx(JSONArray parents, JSONArray inputs, JSONArray outputs, String parentBeaconHashString) {
		
		ArrayList<Sha256Hash> cleanedParents = new ArrayList<Sha256Hash>();
		
		for(int i = 0; i < parents.length(); i++) {
			try {
				Sha256Hash parent = new Sha256Hash(parents.getString(i));
				if(!cleanedParents.contains(parent))
					cleanedParents.add(parent);

			}catch(JSONException|IllegalArgumentException e) {}
		}
		
		ArrayList<Sha256Hash> cleanedInputs = new ArrayList<Sha256Hash>();
		
		for(int i = 0; i < inputs.length(); i++) {
			try {
				Sha256Hash input = new Sha256Hash(inputs.getString(i));
				if(!cleanedInputs.contains(input))
					cleanedInputs.add(input);
			}catch(JSONException|IllegalArgumentException e) {}
		}
		
		ArrayList<TxOutput> cleanedOutputs = new ArrayList<TxOutput>();
		
		for(int i = 0; i < outputs.length(); i++) {
			try {
				TxOutput output = TxOutput.fromString(outputs.getString(i), null);				
				cleanedOutputs.add(output);
			}catch(JSONException | ArithmeticException | IllegalArgumentException e) {}
		}
		
		Sha256Hash parentBeaconHash = null;
		
		try {
			parentBeaconHash = new Sha256Hash(parentBeaconHashString);
		}catch(IllegalArgumentException|NullPointerException e) {}
		
		return new CleanedTx(cleanedParents, cleanedInputs, cleanedOutputs, parentBeaconHash);
	}
	
	/**
	 * Register that orphanTx is waiting for txs so we try to load it once every txs are loaded
	 */
	public synchronized void addWaitedTxs(ArrayList<Sha256Hash> txs, OrphanTransaction orphanTx) {
		
		for(Sha256Hash tx : txs) {
			if(waitedTxs.containsKey(tx))
				waitedTxs.get(tx).add(orphanTx);
			else
				waitedTxs.put(tx, Collections.synchronizedList(new ArrayList<OrphanTransaction>(Arrays.asList(orphanTx))));
		}
		
		waitingTxsHashes.add(orphanTx.getHash());
	}
	
	/**
	 * Check if any transaction is waiting for this one and remove it from waited list
	 */
	public void removeWaitedTx(Sha256Hash tx) {
		if(!waitedTxs.containsKey(tx))
			return;
		
		synchronized(waitedTxs.get(tx)) {
			for(OrphanTransaction orphanTx : waitedTxs.get(tx))
				orphanTx.removeWaitedTx(tx);
		}

		
		waitedTxs.remove(tx);
	}
	
	/**
	 * Get best tips ids to use as parent for a new transaction
	 */
	public ArrayList<Sha256Hash> getBestParents() {
		ArrayList<Sha256Hash> bestParents = new ArrayList<Sha256Hash>();
		while(bestParents.size() == 0) {//avoid desync probs, there can't be 0 childLess txs
			if(childLessTxs.size() == 1) {
				bestParents.add(childLessTxs.get(0).getHash());
			}else if(childLessTxs.size() >= 2) {
				bestParents.add(childLessTxs.get(0).getHash());
				bestParents.add(childLessTxs.get(1).getHash());
			}
		}
		
		return bestParents;
	}

	public LoadedTransaction getBestTipBeacon() {
		LoadedTransaction selectedBeacon = null;
		
		synchronized(childLessBeacons) {
			for(LoadedTransaction beacon : childLessBeacons) {
				if(selectedBeacon == null) {
					selectedBeacon = beacon;
					continue;
				}
				
				if(selectedBeacon.getWeight().compareTo(beacon.getWeight()) < 0) {
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
		}
		return selectedBeacon;
	}
	
	/**
	 * @param uid A transaction id
	 * @return true if the transaction is loaded or is present in database, false otherwise
	 */
	public boolean hasTransaction(Sha256Hash uid) {
		if(loadedTransactions.containsKey(uid) || waitingTxsHashes.contains(uid))
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
	public JSONObject getTxJSON(Sha256Hash uid) {
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
	public boolean isTxWaiting(Sha256Hash txUid) {
		return waitingTxsHashes.contains(txUid);
	}
	
	public EventListener getEventListener() {
		return eventListener;
	}
	
	/**
	 * @param txUid A transaction ID
	 * @return true if transaction is loaded, false otherwise
	 */
	public boolean isLoaded(Sha256Hash txUid) {
		return loadedTransactions.containsKey(txUid);
	}

	public long loadedTxsCount() {
		return loadedTransactions.size();
	}
	
	public long getPoolSize() {
		// TODO Auto-generated method stub
		return waitingTxsHashes.size();
	}

	public LoadedTransaction getGenesis() {
		return genesis;
	}
	
	public static BigInteger calcDifficulty(List<BigInteger> targets, List<Long> solveTimes) {
		
		int T = 60;
		int N = 22;

		BigInteger sumD = BigInteger.valueOf(0);
		double sumST = 0;
		
		for (long solveTime : solveTimes) { 
			sumD = sumD.add(targets.get(solveTimes.indexOf(solveTime))); 
		   if (solveTime > 7*T) {solveTime = 7*T; }
		   if (solveTime < -6*T) {solveTime = -6*T; }
		   sumST += solveTime;
		}
		sumST = 0.75*N*T + 0.2523*sumST;
		return sumD.multiply(BigInteger.valueOf(T)).divide(BigInteger.valueOf((long) sumST));
		
	}
	
	public ArrayList<Sha256Hash> getTipsUids() {
		ArrayList<Sha256Hash> uids = new ArrayList<Sha256Hash>();
		
		synchronized(childLessTxs) {
			for(LoadedTransaction tip : childLessTxs) {
				uids.add(tip.getHash());
			}
		}
		
		return uids;
	}
	
	public LoadedTransaction[] getTips() {
		return childLessTxs.toArray(new LoadedTransaction[childLessTxs.size()]);
	}
	
	public class addTxTask implements Runnable {

		private Transaction transaction;
		
		public addTxTask(Transaction transaction) {
			this.transaction = transaction;
		}
		
		@Override
		public void run() {
			if(transaction.isBeaconTransaction())
				initBeaconTx(transaction);
			else
				initTx(transaction);
		}
		
	}
	
	public class CleanedTx {
		
		private ArrayList<Sha256Hash> parents;
		private ArrayList<Sha256Hash> inputs;
		private ArrayList<TxOutput> outputs;
		private Sha256Hash parentBeaconHash;
		
		public CleanedTx(ArrayList<Sha256Hash> parents, ArrayList<Sha256Hash> inputs, ArrayList<TxOutput> outputs, Sha256Hash parentBeaconHash) {
			this.parents = parents;
			this.inputs = inputs;
			this.outputs = outputs;
			this.parentBeaconHash = parentBeaconHash;
		}
		
	}
	
}
