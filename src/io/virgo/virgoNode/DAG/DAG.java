package io.virgo.virgoNode.DAG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Events.EventListener;
import io.virgo.virgoNode.DAG.Infos.DAGInfos;
import io.virgo.virgoNode.Data.TxLoader;
import io.virgo.virgoNode.Data.TxWriter;
import io.virgo.virgoNode.network.Peers;

/**
 * Represents transactions Directed Acyclic Graph data structure
 * Contains methods to add and fetch transactions
 */
public class DAG implements Runnable {

	private HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>();
	private ConcurrentHashMap<Sha256Hash, List<OrphanTransaction>> waitedTxs = new ConcurrentHashMap<Sha256Hash, List<OrphanTransaction>>();
	//CopyOnWriteArrayList permits to safely write on theses list with the DAG thread while other threads are reading
	protected Set<Sha256Hash> waitingTxsHashes = Collections.synchronizedSet(new HashSet<Sha256Hash>());
	protected CopyOnWriteArrayList<LoadedTransaction> childLessTxs = new CopyOnWriteArrayList<LoadedTransaction>();
	protected CopyOnWriteArrayList<LoadedTransaction> childLessBeacons = new CopyOnWriteArrayList<LoadedTransaction>();

	protected HashMap<String, BeaconBranch> branches = new HashMap<String, BeaconBranch>();
	protected HashMap<String, TxOutput> outputs = new HashMap<String, TxOutput>();
	
	PriorityBlockingQueue<txTask> queue;
	
	private LoadedTransaction genesis;
	
	public long weight = 0;
	
	public TxWriter writer;
	public TxLoader loader;
	
	public int saveInterval;
	
	private EventListener eventListener;
	
	public TxVerificationPool verificationPool;
	
	public DAGInfos infos = new DAGInfos();
	
	public Pruner pruner = new Pruner();
	
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    
	public DAG(int saveInterval) {		
		this.saveInterval = saveInterval;
		
		//setup comparator for task prioritisation 
		Comparator<txTask> comparator = new Comparator<txTask>() {

			@Override
			public int compare(txTask arg0, txTask arg1) {
				int res = arg1.priority - arg0.priority;
				if(res != 0)
					return res;
				
				long datediff = arg0.tx.getDate() - arg1.tx.getDate();
				
				if(datediff < 0)
					return -1;
				else if(datediff > 0)
					return 1;
				
				return arg0.taskNumber - arg1.taskNumber;
			}
			
		};
		
		queue = new PriorityBlockingQueue<txTask>(100, comparator);
	}

	//DAG thread receives raw verified transactions and try to load them
	public void run() {
		
		//start event listener thread
		eventListener = new EventListener(this);
		(new Thread(eventListener)).start();
		
		//Add genesis transaction to DAG
		TxOutput out = new TxOutput("V2N5tYdd1Cm1xqxQDsY15x9ED8kyAUvjbWv", (long) (100000 * Math.pow(10, Main.DECIMALS)));
		TxOutput[] genesisOutputs = {out};
		
		genesis = new LoadedTransaction(genesisOutputs);
		transactions.put(genesis.getHash(), genesis.baseTransaction);
		childLessTxs.add(genesis);
		
		System.out.println("Genesis TxUid is " + genesis.getHash().toString());

		
		//start transaction writer thread (writes transactions to disk)
		writer = new TxWriter(this);
		new Thread(writer).start();
		
		//start transaction loader thread (fetch transactions from disk)
		loader = new TxLoader(this);
		new Thread(loader).start();
		
		//Start transaction verification pool
		verificationPool = new TxVerificationPool(this);
		
		//start transaction pruner
		new Thread(pruner).start();
		
		final DAG dag = this;
		//load saved transactions
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Main.getDatabase().loadAllTransactions(dag);
				} catch (SQLException e1) {
					System.out.println("Unable to load saved transactions: " + e1.getMessage());
				}
			}
			
		}).start();

		
		//ask missing transactions and latest tips to peers every 5s
		new Timer().scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				try {
					
					Peers.getTips();
					
					int i = 0;
					
					while(true) {
						if(i >= childLessTxs.size())
							break;
						
						Peers.askChilds(childLessTxs.get(i).getHash(), 100);
						i++;
						
						Thread.sleep(500);
					}
										
					if(waitedTxs.size() != 0) {
						ArrayList<Sha256Hash> lakingTransactions = new ArrayList<Sha256Hash>(waitedTxs.keySet());
						lakingTransactions.removeAll(Arrays.asList(waitingTxsHashes.toArray(new Sha256Hash[1])));
						
						LoadedTransaction selectedTip = null;
						for(LoadedTransaction tip : Main.getDAG().getTips())
							if(selectedTip == null || selectedTip.getDate() < tip.getDate())
								selectedTip = tip;
						
						i = 0;
						
						while(true) {
							if(i >= lakingTransactions.size() || i >= 20)//limit to 20 so we periodically refresh lakingTransactions list
								break;
							
							Peers.askParents(lakingTransactions.get(i), selectedTip.getHash(), 10);
							i++;
							
							Thread.sleep(250);
						}
						
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		}, 5000, 10000);
		
		while(!Thread.interrupted()) {
			
			try {
				
				txTask task = queue.take();
				//load task
				if(task.loadedParents != null) {
					if(task.tx.isBeaconTransaction())
						loadBeaconTx(task.tx, task.loadedParents, task.parentBeacon);
					else
						loadTx(task.tx, task.loadedParents, task.loadedInputs);
				}
				else{//verif task
					if(task.tx.isBeaconTransaction()) {
						checkBeaconTx(task.tx);

					}else {
						checkTx(task.tx);
					}

				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	public LoadedTransaction getLoadedTx(Sha256Hash hash) {
		Transaction tx = transactions.get(hash);
		if(tx != null)
			return tx.getLoaded();
		
		return null;
	}
	
	public LoadedTransaction getLoadedTxSafe(Sha256Hash hash) {
		Transaction tx;
		while(true) {
			tx = transactions.get(hash);
			if(tx != null)
				return tx.getLoaded();
		}
	}
	
	public Transaction getTx(Sha256Hash hash) {
		return transactions.get(hash);
	}
	
	/**
	 * Check if beacon has all it's parents loaded, if so send it to verification pool
	 * to check that it's randomX hash match the required difficulty
	 */
	private void checkBeaconTx(Transaction tx) {
		if(transactions.containsKey(tx.getHash()) || waitingTxsHashes.contains(tx.getHash()))
			return;
		
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
		
		if(parentBeacon == null && !waitedTxs.contains(tx.getParentBeaconHash()))
			waitedTxs.add(tx.getParentBeaconHash());
		
		//if there is any missing transaction (input or parent) try to load them and add this transaction to waiting txs
		if(!waitedTxs.isEmpty()) {
			addWaitedTxs(waitedTxs, new OrphanTransaction(tx, waitedTxs.toArray(new Sha256Hash[waitedTxs.size()])));
			loader.push(waitedTxs);
			return;
		}
		
		verificationPool.new beaconVerificationTask(tx, parentBeacon, loadedParents);
	}
	
	/**
	 * Add the given beacon to the DAG, at this point it's fully verified and safe to do so
	 */
	private void loadBeaconTx(Transaction tx, ArrayList<LoadedTransaction> loadedParents, LoadedTransaction parentBeacon) {
		
		if(transactions.containsKey(tx.getHash()) || waitingTxsHashes.contains(tx.getHash()))
			return;
		
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), parentBeacon);
		transactions.put(loadedTx.getHash(), loadedTx.baseTransaction);
		
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
				
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getHash());	
		
	}
	
	/**
	 * Check if this transaction's related txs are loaded
	 * if so send it to verification pool for differents checks
	 */
	private void checkTx(Transaction tx) {
		
		if(transactions.containsKey(tx.getHash()))
			return;
		
		if(waitingTxsHashes.contains(tx.getHash()))
			return;

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
			return;
		}
		
		verificationPool.new transactionVerificationTask(tx, loadedParents, loadedInputs);
	}
	
	/**
	 * Add the given transaction to the DAG, safe to do as it's fully verified at this point
	 */
	private void loadTx(Transaction tx, ArrayList<LoadedTransaction> loadedParents, ArrayList<LoadedTransaction> loadedInputs) {
		
		if(transactions.containsKey(tx.getHash()) || waitingTxsHashes.contains(tx.getHash()))
			return;
		
		//transmit tx to peers
		JSONObject txInv = new JSONObject();	
		txInv.put("command", "inv");
		txInv.put("ids", new JSONArray(Arrays.asList(tx.getHash().toString())));
		
		Main.getGeoWeb().broadCast(txInv);
		
		//load transaction to DAG
		LoadedTransaction loadedTx = new LoadedTransaction(tx, loadedParents.toArray(new LoadedTransaction[loadedParents.size()]), loadedInputs.toArray(new LoadedTransaction[loadedInputs.size()]));
		
		transactions.put(loadedTx.getHash(), loadedTx.baseTransaction);
		
		//save transaction if not done yet
		if(!loadedTx.isSaved())
			loadedTx.save();
		
		//try to load any transaction that was waiting for this one to load
		removeWaitedTx(loadedTx.getHash());
		
	}
	
	/**
	 * Register that orphanTx is waiting for txs so we try to load it once every txs are loaded
	 */
	private void addWaitedTxs(ArrayList<Sha256Hash> txs, OrphanTransaction orphanTx) {
		if(waitingTxsHashes.contains(orphanTx.getHash()))//can't happen ?
			return;
		
		for(Sha256Hash tx : txs) {
			if(waitedTxs.containsKey(tx))
				waitedTxs.get(tx).add(orphanTx);
			else
				waitedTxs.put(tx, new ArrayList<OrphanTransaction>(Arrays.asList(orphanTx)));
		}
		
		waitingTxsHashes.add(orphanTx.getHash());
	}
	
	/**
	 * Check if any transaction is waiting for this one and remove it from waited list
	 */
	private void removeWaitedTx(Sha256Hash tx) {
		if(!waitedTxs.containsKey(tx))
			return;
			
			for(OrphanTransaction orphanTx : waitedTxs.get(tx))
				orphanTx.removeWaitedTx(tx, this);
		
		waitedTxs.remove(tx);
	}
	
	/**
	 * Get best tips ids to use as parent for a new transaction
	 */
	public ArrayList<Sha256Hash> getBestParents(LoadedTransaction parentBeacon) {
		
		ArrayList<Sha256Hash> bestParents = new ArrayList<Sha256Hash>();
		b: while(bestParents.size() == 0) {//avoid desync probs, there can't be 0 childLess txs
			for(LoadedTransaction tx : childLessTxs) {
				if(bestParents.size() == 5) break b;
				if(!bestParents.contains(tx.getHash()) && tx.isChildOf(parentBeacon))
					bestParents.add(tx.getHash());
			}
			
			if(bestParents.size() == 0 && !bestParents.contains(parentBeacon.getHash())) {
				bestParents.add(parentBeacon.getHash());
			}
			
			for(LoadedTransaction tx : childLessTxs) {
				if(bestParents.size() == 5) break b;
				if(!bestParents.contains(tx.getHash()))
					bestParents.add(tx.getHash());
			}
		}
		
		return bestParents;
	}

	/**
	 * Get the best suited beacon for proof of work
	 * Select the tip beacon with the most weight (solved difficulty)
	 * If there is other tips with the same weight select the highest one (chain height)
	 * If there is other tips with the same weight and height select the one with lower date
	 */
	public LoadedTransaction getBestTipBeacon() {
		LoadedTransaction selectedBeacon = null;
		for(LoadedTransaction beacon : childLessBeacons) {
			if(selectedBeacon == null) {
				selectedBeacon = beacon;
				continue;
			}
			
			if(selectedBeacon.getFloorWeight().compareTo(beacon.getFloorWeight()) < 0) {
				selectedBeacon = beacon;
				continue;
			}
			
			if(selectedBeacon.getFloorWeight().compareTo(beacon.getFloorWeight()) == 0) {
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
	public boolean hasTransaction(Sha256Hash uid) {
		if(transactions.containsKey(uid) || waitingTxsHashes.contains(uid))
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
		if(transactions.containsKey(uid)) {
			return transactions.get(uid).toJSONObject();
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
		return transactions.containsKey(txUid);
	}

	public long loadedTxsCount() {
		return transactions.size();
	}
	
	public long getPoolSize() {
		// TODO Auto-generated method stub
		return waitingTxsHashes.size();
	}

	public LoadedTransaction getGenesis() {
		return genesis;
	}
	
	public ArrayList<Sha256Hash> getTipsUids() {
		ArrayList<Sha256Hash> uids = new ArrayList<Sha256Hash>();
		
		for(LoadedTransaction tip : childLessTxs) {
			uids.add(tip.getHash());
		}
		
		return uids;
	}
	
	public LoadedTransaction[] getTips() {
		return childLessTxs.toArray(new LoadedTransaction[childLessTxs.size()]);
	}
	
	
	public class txTask {
				
		Transaction tx = null;
		ArrayList<LoadedTransaction> loadedParents = null;
		ArrayList<LoadedTransaction> loadedInputs = null;
		LoadedTransaction parentBeacon = null;
		
		int priority = 0;
		int taskNumber = taskCounter.incrementAndGet();
				
		public txTask(Transaction tx) {
			this.tx = tx;
		}
		
		public txTask(Transaction tx, ArrayList<LoadedTransaction> loadedParents, LoadedTransaction parentBeacon) {
			this.tx = tx;
			this.loadedParents = loadedParents;
			this.parentBeacon = parentBeacon;
			priority = 2;
		}
		
		public txTask(Transaction tx, ArrayList<LoadedTransaction> loadedParents, ArrayList<LoadedTransaction> loadedInputs) {
			this.tx = tx;
			this.loadedParents = loadedParents;
			this.loadedInputs = loadedInputs;
			priority = 2;
		}
		
	}
	
}
