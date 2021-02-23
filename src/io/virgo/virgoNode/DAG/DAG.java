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
	private ConcurrentHashMap<String, LoadedTransaction> loadedTransactions = new ConcurrentHashMap<String, LoadedTransaction>();
	protected List<LoadedTransaction> mainChain = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	protected NavigableMap<Long, LoadedTransaction> nodesToCheck = Collections.synchronizedNavigableMap(new TreeMap<Long, LoadedTransaction>());
	ConcurrentHashMap<String, List<OrphanTransaction>> waitedTxs = new ConcurrentHashMap<String, List<OrphanTransaction>>();
	protected List<String> waitingTxsUids = Collections.synchronizedList(new ArrayList<String>());
	List<LoadedTransaction> childLessTxs = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	
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
		TxOutput out = new TxOutput("V2N5tYdd1Cm1xqxQDsY15x9ED8kyAUvjbWv", Main.TOTALUNITS, "", "");
		TxOutput[] genesisOutputs = {out};
		
		LoadedTransaction genesis = new LoadedTransaction(this, genesisOutputs);
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
	
	/**
	 * Initiate transaction from raw data
	 * Includes checks for data and signature validity 
	 */
	public void initTx(byte[] sigBytes, byte[] pubKey, JSONArray parents, JSONArray inputs, JSONArray outputs, long date, boolean saved) throws IllegalArgumentException {
		
		String txUid = Converter.Addressify(sigBytes, Main.TX_IDENTIFIER);
		String address = Converter.Addressify(pubKey, Main.ADDR_IDENTIFIER);
		
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
			
			Sha256Hash TxHash = Sha256.getHash((parents.toString() + inputs.toString() + outputs.toString() + date).getBytes());
			if(!signer.Verify(TxHash, sig, pubKey)) {
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
			TxOutput output = TxOutput.fromString(outputs.getString(i), txUid, address);
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
		
		String[] parentsUids = tx.getParentsUids();
		
		//Check for missing parents 
		ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
		for(String parentTxUid : parentsUids) {
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
		if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0))))
			return;

		
		long totalInputValue = 0;
		
		//check if inputs are valid and calculate inputs total value
		for(LoadedTransaction input : loadedInputs) {
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
		if(totalInputValue < tx.getOutputsValue()) {
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
		
		//try to load any tranasaction that was waiting for this one to load
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
				TxOutput output = TxOutput.fromString(outputs.getString(i), "", "");				
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
