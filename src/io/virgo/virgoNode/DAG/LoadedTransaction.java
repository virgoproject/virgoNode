package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map.Entry;

import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.DAG.Events.TransactionLoadedEvent;
import io.virgo.virgoNode.DAG.Events.TransactionStatusChangedEvent;

/**
 * Object representing a loaded Transaction
 * Extends base transaction
 */
public class LoadedTransaction extends Transaction {
	
	private DAG dag;
	
	LinkedHashMap<BeaconBranch, BigInteger> beaconBranchs = new LinkedHashMap<BeaconBranch, BigInteger>();//branch displacement
		
	private ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
	
	private int height = 0;
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<LoadedTransaction> loadedInputTxs = new ArrayList<LoadedTransaction>();
	
	private long inputsValue = 0;
		
	private volatile TxStatus status = TxStatus.PENDING;
	
	//beacon related variables
	private BigInteger difficulty;
	private BigInteger floorWeight;
	private long beaconHeight;
	private LoadedTransaction loadedParentBeacon;
	private ArrayList<Sha256Hash> childBeacons = new ArrayList<Sha256Hash>(); 
	public ArrayList<LoadedTransaction> loadedChildBeacons = new ArrayList<LoadedTransaction>();
	private boolean mainChainMember = false;
	private boolean confirmedParents = false;
	private ArrayList<Sha256Hash> conflictualTxsHashes = new ArrayList<Sha256Hash>();
	private ArrayList<LoadedTransaction> conflictualTxs = new ArrayList<LoadedTransaction>();
	private List<Long> solveTimes = new ArrayList<Long>();//solveTimes of the last 27 parent blocks
	private List<BigInteger> difficulties = new ArrayList<BigInteger>();//difficulties of the last 27 parent blocks
	
	private Sha256Hash randomX_key = null;
	private Sha256Hash practical_randomX_key = null;
	
	private LoadedTransaction settlingTransaction;
	private Sha256Hash settlingTransactionHash;
	
	/**
	 * Basic transaction constructor
	 */
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction[] inputTxs) {
		
		super(baseTransaction);
		
		this.dag = dag;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		this.loadedInputTxs.addAll(Arrays.asList(inputTxs));
		
		//calculate inputs value
		for(LoadedTransaction inputTx : inputTxs) {
			TxOutput out = inputTx.getOutputsMap().get(getAddress());
			out.claimers.add(this);
			inputsValue += out.getAmount();
		}
		
		//Add this transaction to tips list
		dag.childLessTxs.add(this);
		
		for(LoadedTransaction parent : loadedParents) {			
			//Remove parent from tips list if in it
			dag.childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(loadedParents.size() == 1)
			height = loadedParents.get(0).getHeight() + 1;
		else
			for(LoadedTransaction parent : loadedParents)
				if(parent.getHeight() > height-1)
					height = parent.getHeight() + 1;
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * genesis constructor
	 */
	public LoadedTransaction(DAG dag, TxOutput[] genesisOutputs) {
		super(genesisOutputs);
		
		this.dag = dag;
		
		status = TxStatus.CONFIRMED;
		
		//set base difficulty
		difficulty = BigInteger.valueOf(10000);
		floorWeight = BigInteger.ZERO;
		
		mainChainMember = true;
		confirmedParents = true;
		dag.childLessBeacons.add(this);
		
		randomX_key = getHash();
		practical_randomX_key = getHash();
		
		//Prefill difficulties and solveTimes with perfect values to smooth first blocks difficulty drop
		for(int i = 0; i < 27; i++) {
			difficulties.add(difficulty);
			solveTimes.add(30l);
		}
		
		settlingTransaction = this;
		settlingTransactionHash = getHash();
		
		//create base beacon branch
		BeaconBranch beaconBranch = new BeaconBranch();
		beaconBranch.addTx(this);
		beaconBranchs.put(beaconBranch, BigInteger.ZERO);
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * Beacon transaction constructor
	 */
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction parentBeacon) {
		super(baseTransaction);
		
		this.dag = dag;
		
		settlingTransaction = this;
		settlingTransactionHash = getHash();
		
		this.loadedParents.addAll(Arrays.asList(parents));
		
		this.loadedParentBeacon = parentBeacon;
		loadedParentBeacon.loadedChildBeacons.add(this);
		loadedParentBeacon.childBeacons.add(getHash());
		
		beaconHeight = loadedParentBeacon.getBeaconHeight() + 1;
		
		floorWeight = loadedParentBeacon.floorWeight.add(loadedParentBeacon.difficulty);
		
		dag.childLessBeacons.remove(loadedParentBeacon);
		dag.childLessBeacons.add(this);
		
		//Calculate next beacon difficulty
		difficulty = calcDifficulty(loadedParentBeacon.getDifficulties(), loadedParentBeacon.getSolveTimes());
		
		//Update difficulties and solvetimes arrays for next beacon difficulty calculation
		difficulties = loadedParentBeacon.getDifficulties();
		solveTimes = loadedParentBeacon.getSolveTimes();
		
		if(difficulties.size() == 27)
			difficulties.remove(0);
		difficulties.add(difficulty);
		
		if(solveTimes.size() == 27)
			solveTimes.remove(0);
		solveTimes.add((getDate()-loadedParentBeacon.getDate())/1000);
		
		//Change the randomX key to this transaction hash if if it is a multiple of 2048
		if(beaconHeight % 2048 == 0)
			randomX_key = getHash();
		else
			randomX_key = parentBeacon.randomX_key;
		
		/*
		 * Get the randomX key 64 beacons behind and use it for the next beacon
		 * So there is a 64 beacons delay between randomX key change and effectiveness
		 */
		LoadedTransaction beacon64old = this;
		for(int i = 0; i < 64; i++) {
			if(beacon64old.isGenesis())
				break;
			beacon64old = beacon64old.getParentBeacon();
		}
		
		practical_randomX_key = beacon64old.randomX_key;
		
		
		//Add this transaction to tips list
		dag.childLessTxs.add(this);
		
		for(LoadedTransaction parent : loadedParents) {			
			//Remove parent from tips list if in it
			dag.childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(loadedParents.size() == 1)
			height = loadedParents.get(0).getHeight() + 1;
		else
			for(LoadedTransaction parent : loadedParents)
				if(parent.getHeight() > height-1)
					height = parent.getHeight() + 1;
		
		
		setupBeaconBranch();
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * Add this beacon to parent beacon's branch or create a new branch
	 * if it's a fork (parent already has a child)
	 * 
	 * Branchs unable optimization of beacon weight and confirmations count calculation
	 */
	private void setupBeaconBranch() {
		
		if(loadedParentBeacon.childBeacons.size() == 1) {//transaction is parent's first child, make part of parent's main branch
			BeaconBranch parentMainBranch = loadedParentBeacon.getMainBeaconBranch();
			beaconBranchs.put(parentMainBranch, parentMainBranch.addTx(this));
		} else {
			
			//create branch
			BeaconBranch branch = new BeaconBranch();
			branch.addTx(this);
			beaconBranchs.put(branch, BigInteger.ZERO);
			
			//add branch to parent transactions branchs
			for(LoadedTransaction parentChainMember : loadedParentBeacon.getMainBeaconBranch().getMembersBefore(loadedParentBeacon))
				parentChainMember.beaconBranchs.put(branch, BigInteger.ZERO);
		
		}
		
		
		chooseNextBeacon();
	}
	
	/**
	 * Update beacon chain
	 * If the current beacon is not part of the main chain then recursively
	 * call this function on parent beacons until reaching the main chain
	 * 
	 * once on a mainchain beacon, confirm the child with most weight and refuse others
	 * If there is equality between two major beacon then unconfirm all major beacon until
	 * resolution
	 * 
	 * When a beacon is refused or unconfirmed it does the same all it's child
	 * 
	 * When confirming a beacon, we first run setSettler() which mark all parent transactions
	 * until reaching parent beacon domain, and also check for conflictual transactions
	 * 
	 * Then, confirm all marked transactions and resolve conflictual ones by confirming the one
	 * with more weight, or refuse all if none
	 */
	private void chooseNextBeacon() {
		
		if(!mainChainMember) {
			loadedParentBeacon.chooseNextBeacon();
			return;
		}

		if(!confirmedParents)
			confirmParents();
		
		LoadedTransaction mainChainBeaconChild = null;
		List<LoadedTransaction> lst = new ArrayList<LoadedTransaction>();
		BigInteger pounds = BigInteger.ZERO;
		
		for(LoadedTransaction e : loadedChildBeacons){
			if(e.mainChainMember)
				mainChainBeaconChild = e;
			
			if(e.getWeight().compareTo(pounds) > 0){
				lst.clear();
				pounds = e.getWeight();
				lst.add(e);
			}
			else if(e.getWeight().compareTo(pounds) == 0)
				lst.add(e);
		}
		
	    if (lst.size() == 1 && !lst.get(0).equals(mainChainBeaconChild)) {
	        
	    	lst.get(0).mainChainMember = true;
	    	
	        for (LoadedTransaction e : loadedChildBeacons) {
		          if (e.equals(lst.get(0)))
		            continue; 
		          e.undoChain();
		          e.rejectTx();
		    } 
	    	
	        lst.get(0).chooseNextBeacon();
	        
	    }else if(lst.size() > 1 && mainChainBeaconChild != null)
	    	mainChainBeaconChild.undoChain();
		
	}
	
	/**
	 * Confirm this beacon and resolve settled transactions conflicts
	 */
	private void confirmParents() {
		confirmedParents = true;
		confirmTx();
		
		for(LoadedTransaction parent : loadedParents)
				parent.setSettler(this);
		
		for(LoadedTransaction conflictingTransaction : conflictualTxs) {
			
			boolean canConfirm = true;
			b:
			for(TxOutput input : conflictingTransaction.loadedInputs) {
				for(LoadedTransaction claimer : input.claimers)
					if(!claimer.equals(conflictingTransaction) && conflictualTxs.contains(claimer)) {
						canConfirm = false;
						break b;
					}
			}
			
			if(canConfirm)
				conflictingTransaction.confirmTx();
			else
				conflictingTransaction.rejectTx();
			
		}
		
	}
	
	/**
	 * Settle this transaction and parent transactions with given beacon
	 * until reaching already settled transactions (excluding beacons) or mainchain.
	 * 
	 * Settling is confirming transactions that aren't conflicting with other
	 * and adding conflictual transactions to a list for later solving in confirmParents()
	 */
	private void setSettler(LoadedTransaction tx) {
		
		Stack<LoadedTransaction> s = new Stack<LoadedTransaction>();
		LoadedTransaction tmp;
		
		if(settlingTransaction == null) {
			settlingTransaction = tx;
			settlingTransactionHash = tx.getHash();
		}
		
		s.add(this);
		
		while(!s.isEmpty()){
			tmp = s.pop();
			
			//if element is from mainchain we don't add it's parents and dont proccess it
			if(tmp.isMainChainMember())
				continue;
			
			for(LoadedTransaction e : tmp.getLoadedParents()){
				//if beacon transaction we add it to continue walking but don't change it's settlingTransaction
				if(e.isBeaconTransaction()) {
					s.add(e);
					continue;
				}
				
				if(e.settlingTransaction == null){
					e.settlingTransaction = tx;
					e.settlingTransactionHash = tx.getHash();
					s.add(e);
				}
			}
			
			//if beacon transaction don't process it
			if(tmp.isBeaconTransaction())
				continue;
			
			boolean canConfirm = true;
			
			//if any input is already spent refuse this transaction
			for(LoadedTransaction inputTx : tmp.loadedInputTxs)
				if(inputTx.getStatus().isRefused() || inputTx.getOutputsMap().get(tmp.getAddress()).isSpent()){
					tmp.rejectTx();
					canConfirm = false;
					break;
				}
			
			if(canConfirm) {//run only if transaction hasn't been refused on last check
				b: for(TxOutput input : tmp.loadedInputs)
					for(LoadedTransaction claimer : input.claimers)
						if(claimer != this && !claimer.getStatus().isRefused()) {
							settlingTransaction.conflictualTxs.add(tmp);
							settlingTransaction.conflictualTxsHashes.add(tmp.getHash());
							canConfirm = false;
							break b;
						}
				if(canConfirm)
					tmp.confirmTx();
			}
			
		}
	}
	
	/**
	 * reset this confirmed beacon and all it's child confirmed beacons
	 * As well as the transactions that they confirmed
	 */
	private void undoChain() {
		if(!mainChainMember)
			return;
		
		changeStatus(TxStatus.PENDING);
		mainChainMember = false;
		confirmedParents = false;
		conflictualTxs.clear();
		conflictualTxsHashes.clear();
		
		for(LoadedTransaction parent : loadedParents)
			parent.removeSettler(this);
		
		LoadedTransaction mainChainBeaconChild = null;
		
		for(LoadedTransaction childBeacon : loadedChildBeacons) {
			if(childBeacon.mainChainMember) {
				mainChainBeaconChild = childBeacon;
				break;
			}
		}
		
		if(mainChainBeaconChild != null)
			mainChainBeaconChild.undoChain();
	}
	
	/**
	 * Remove given settler from this and parent transactions until
	 * reaching a transaction that hasn't the given beacon as settler
	 * 
	 * UndoChain subfunction 
	 */
	private void removeSettler(LoadedTransaction settler) {
		if(settlingTransaction != settler)
			return;
		
		Stack<LoadedTransaction> s = new Stack<LoadedTransaction>();
		s.add(this);
		
		settlingTransaction = null;
		settlingTransactionHash = null;
		
		changeStatus(TxStatus.PENDING);
		
		while(!s.isEmpty())
		{
			LoadedTransaction tmp = s.pop();
			
			for(LoadedTransaction parent : tmp.loadedParents)
			{
				if(parent.settlingTransaction != settler)
					continue;
				
				parent.settlingTransaction = null;
				parent.settlingTransactionHash = null;
				
				parent.changeStatus(TxStatus.PENDING);
				s.add(parent);
			}
		}
	}
	
	/**
	 * Checks if this transaction is a direct child of target transaction
	 * For this we walk backward through the DAG until we find the target transaction or
	 * reach target transaction height
	 * 
	 * @param target The target transaction
	 * @return true if this transaction is a direct child of target, false otherwise
	 */
	public boolean isChildOf(LoadedTransaction target) {
		if(height < target.height)
			return false;
		
		if(height == target.height)
			return target == this;
		
		ArrayList<ArrayList<LoadedTransaction>> tab = new ArrayList<ArrayList<LoadedTransaction>>();
		for(int i = 0; i < height - target.height - 1; i++)
			tab.add(i, new ArrayList<LoadedTransaction>());
		
        Stack<LoadedTransaction> stack = new Stack<LoadedTransaction>();
        stack.push(this);

	    while(!stack.isEmpty())
	    {
	    		LoadedTransaction current = stack.pop(); 
	            for(LoadedTransaction parent : current.getLoadedParents()) {
	
	            if(parent.height < target.height)
	                continue;
	            
	                if(parent.height == target.height) {
	                    if(parent == target)
	                        return true;
	                    continue;
	                }
	
	                if(!tab.get(parent.height - target.height - 1).contains(parent)) {
	                    tab.get(parent.height - target.height - 1).add(parent);
	                    stack.push(parent);
	                }
	            }
	    }
        
        return false;
	}
	
	private void changeStatus(TxStatus newStatus) {
		TxStatus formerStatus = status;
		status = newStatus;
		dag.getEventListener().notify(new TransactionStatusChangedEvent(this, formerStatus));
	}
	
	/**
	 * Confirm this transaction
	 */
	public void confirmTx() {
		changeStatus(TxStatus.CONFIRMED);
	}
	
	/**
	 * Reject this transaction/beacon and subsequent output claimers/child beacons
	 */
	public void rejectTx() {
		changeStatus(TxStatus.REFUSED);
	
		for(TxOutput out : getOutputsMap().values())
			for(LoadedTransaction claimer : out.claimers)
				claimer.rejectTx();
		
		if(isBeaconTransaction())
			for(LoadedTransaction childBeacon : loadedChildBeacons)
				childBeacon.rejectTx();
	}
	
	public void save() {
		dag.writer.push(this);
	}
	
	/**
	 * Zawy's modifed Digishield v3 (tempered SMA) difficulty algorithm
	 */
	private BigInteger calcDifficulty(List<BigInteger> targets, List<Long> solveTimes) {
		
		int T = 30;
		
		BigInteger sumD = BigInteger.valueOf(0);
		double sumST = 0;
		
		for (long solveTime : solveTimes) { 
			sumD = sumD.add(targets.get(solveTimes.indexOf(solveTime))); 
		   if (solveTime > 7*T) {solveTime = 7*T; }
		   if (solveTime < -6*T) {solveTime = -6*T; }
		   sumST += solveTime;
		}
		//sumST = 0.75*T*60
		sumST = 607.5 + 0.2523*sumST;
		return sumD.multiply(BigInteger.valueOf(T)).divide(BigInteger.valueOf((long) sumST));
		
	}
	
	public BeaconBranch getMainBeaconBranch() {
		return beaconBranchs.keySet().iterator().next();
	}
	
	public TxStatus getStatus() {
		return status;
	}
	
	public long getTotalInput() {
		return inputsValue;
	}
	
	public LoadedTransaction[] getLoadedParents() {
		return loadedParents.toArray(new LoadedTransaction[loadedParents.size()]);
	}
	
	public LoadedTransaction getLoadedParent(int index) {
		return loadedParents.get(index);
	}

	public LoadedTransaction getParentBeacon() {
		return loadedParentBeacon;
	}
	
	public int getHeight() {
		return height;
	}
	
	/**
	 * Calculate beacon weight by adding child branches weights 
	 */
	public BigInteger getWeight() {
		BigInteger newWeight = BigInteger.ZERO;

		for(Entry<BeaconBranch, BigInteger> branchEntry : beaconBranchs.entrySet())
			if(branchEntry.getKey().equals(getMainBeaconBranch()))
				newWeight = newWeight.add(branchEntry.getKey().getBranchWeight().subtract(branchEntry.getValue()));
			else
				newWeight = newWeight.add(branchEntry.getKey().getFirst().getWeight());
		
		return newWeight;
	}
	
	public long getBeaconHeight() {
		return beaconHeight;
	}
	
	public boolean isMainChainMember() {
		return mainChainMember;
	}
	
	/**
	 * Get transaction (either beacon or normal) confirmations count
	 * If beacon, get the confirmation count by adding child branches confirmations count
	 * If normal transaction return the settling transaction's confirmation count, or 0 if no settler yet
	 */
	public int confirmationCount() {
		if(settlingTransaction == null)
			return 0;
		
		if(!isBeaconTransaction())
			return settlingTransaction.confirmationCount();
		
		int confirmations = 0;
		for(Entry<BeaconBranch, BigInteger> branchEntry : beaconBranchs.entrySet())
			if(branchEntry.getKey().equals(getMainBeaconBranch()))
				confirmations += branchEntry.getKey().getBranchConfirmations() - branchEntry.getKey().indexOf(this);
			else
				confirmations += branchEntry.getKey().getFirst().confirmationCount();
		
		return confirmations;
	}
	
	

	public LoadedTransaction getSettlingTransaction() {
		return settlingTransaction;
	}
	
	public BigInteger getDifficulty() {
		return difficulty;
	}
	
	public BigInteger getFloorWeight() {
		return floorWeight;
	}
	
	private List<BigInteger> getDifficulties(){	
		return new ArrayList<BigInteger>(difficulties);
	}
	
	private List<Long> getSolveTimes(){		
		return new ArrayList<Long>(solveTimes);
	}
	
	public Sha256Hash getRandomXKey() {
		return practical_randomX_key;
	}
	
	public JSONObject JSONState() {
		JSONObject baseJSON = toJSONObject();
		
		baseJSON.put("height", height);
		
		JSONArray outputsIds = new JSONArray();
		for(TxOutput output : getOutputsMap().values())
			outputsIds.put(output.getUUID());
		baseJSON.put("outputsIds", outputsIds);
		
		if(isBeaconTransaction()) {
		
			JSONArray branches = new JSONArray();
			for(BeaconBranch branch : beaconBranchs.keySet())
				branches.put(branch.getUUID());
			baseJSON.put("branches", branches);
			
			baseJSON.put("floorWeight", floorWeight.toString());
			baseJSON.put("beaconHeight", beaconHeight);
			
			JSONArray childBeaconsHashes = new JSONArray();
			for(Sha256Hash hash : childBeacons)
				childBeaconsHashes.put(hash.toString());
			baseJSON.put("childBeacons", childBeaconsHashes);
			
			baseJSON.put("mainChainMember", mainChainMember);
			baseJSON.put("confirmedParents", confirmedParents);
			
			JSONArray conflictualHashes = new JSONArray();
			for(Sha256Hash hash : conflictualTxsHashes)
				conflictualHashes.put(hash);
			baseJSON.put("conflictualTxs", conflictualHashes);
			
			JSONArray diffs = new JSONArray();
			for(BigInteger diff : difficulties)
				diffs.put(diff.toString());
			baseJSON.put("diffs", diffs);
			
			JSONArray times = new JSONArray();
			for(long solveTime : solveTimes)
				times.put(solveTime);
			baseJSON.put("solveTimes", times);
			
			baseJSON.put("RXKey", randomX_key.toString());
			baseJSON.put("PrRXKey", practical_randomX_key);
			
		}else {
			JSONArray inputsIds = new JSONArray();
			for(TxOutput input : loadedInputs)
				inputsIds.put(input.getUUID());
			baseJSON.put("inputsIds", inputsIds);
		}
		
		if(settlingTransactionHash != null)
			baseJSON.put("settlingTransaction", settlingTransactionHash.toString());
		
		baseJSON.remove("inputs");
		baseJSON.remove("outputs");
		
		return baseJSON;
	}
	
}
