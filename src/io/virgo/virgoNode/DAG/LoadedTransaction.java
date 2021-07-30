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

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.Events.TransactionLoadedEvent;
import io.virgo.virgoNode.DAG.Events.TransactionStatusChangedEvent;

/**
 * Object representing a loaded Transaction
 * Extends base transaction
 */
public class LoadedTransaction extends Transaction {
	
	LinkedHashMap<BeaconBranch, BigInteger> beaconBranchs = new LinkedHashMap<BeaconBranch, BigInteger>();//branch displacement
		
	private ArrayList<Transaction> loadedParents = new ArrayList<Transaction>();
	
	private int height = 0;
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<Transaction> loadedInputTxs = new ArrayList<Transaction>();
	
	private long inputsValue = 0;
		
	private volatile TxStatus status = TxStatus.PENDING;
	
	//beacon related variables
	private BigInteger difficulty;
	private BigInteger floorWeight;
	private long beaconHeight;
	private Transaction loadedParentBeacon;
	public ArrayList<Transaction> loadedChildBeacons = new ArrayList<Transaction>();
	private boolean mainChainMember = false;
	private boolean confirmedParents = false;
	private ArrayList<Transaction> conflictualTxs = new ArrayList<Transaction>();
	private List<Integer> solveTimes = new ArrayList<Integer>();//solveTimes of the last 27 parent blocks
	private List<BigInteger> difficulties = new ArrayList<BigInteger>();//difficulties of the last 27 parent blocks
	
	private Sha256Hash randomX_key = null;
	private Sha256Hash practical_randomX_key = null;
	
	private Transaction settlingTransaction;
	
	/**
	 * Basic transaction constructor
	 */
	public LoadedTransaction(Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction[] inputTxs) {
		
		super(baseTransaction);
		loadedTx = this;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		this.loadedInputTxs.addAll(Arrays.asList(inputTxs));
		
		//calculate inputs value
		for(LoadedTransaction inputTx : inputTxs) {
			TxOutput out = inputTx.getOutputsMap().get(getAddress());
			out.claimers.add(this);
			inputsValue += out.getAmount();
		}
		
		//Add this transaction to tips list
		Main.getDAG().childLessTxs.add(this);
		
		for(LoadedTransaction parent : parents) {			
			//Remove parent from tips list if in it
			Main.getDAG().childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(parents.length == 1)
			height = parents[0].getHeight() + 1;
		else
			for(LoadedTransaction parent : parents)
				if(parent.getHeight() > height-1)
					height = parent.getHeight() + 1;
		
		Main.getDAG().getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * genesis constructor
	 */
	public LoadedTransaction(TxOutput[] genesisOutputs) {
		super(genesisOutputs);
		loadedTx = this;
		
		status = TxStatus.CONFIRMED;
		
		//set base difficulty
		difficulty = BigInteger.valueOf(10000);
		floorWeight = BigInteger.ZERO;
		
		mainChainMember = true;
		confirmedParents = true;
		Main.getDAG().childLessBeacons.add(this);
		
		randomX_key = getHash();
		practical_randomX_key = getHash();
		
		//Prefill difficulties and solveTimes with perfect values to smooth first blocks difficulty drop
		for(int i = 0; i < 27; i++) {
			difficulties.add(difficulty);
			solveTimes.add(30);
		}
		
		settlingTransaction = this;
		
		//create base beacon branch
		BeaconBranch beaconBranch = new BeaconBranch();
		beaconBranch.addTx(this);
		beaconBranchs.put(beaconBranch, BigInteger.ZERO);
		
		Main.getDAG().getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * Beacon transaction constructor
	 */
	public LoadedTransaction(Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction parentBeacon) {
		super(baseTransaction);
		loadedTx = this;
		
		settlingTransaction = this;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		
		this.loadedParentBeacon = parentBeacon;
		parentBeacon.loadedChildBeacons.add(this);
		
		beaconHeight = parentBeacon.getLoaded().getBeaconHeight() + 1;
		
		floorWeight = parentBeacon.floorWeight.add(parentBeacon.difficulty);
		
		Main.getDAG().childLessBeacons.remove(parentBeacon);
		Main.getDAG().childLessBeacons.add(this);
		
		//Calculate next beacon difficulty
		difficulty = calcDifficulty(parentBeacon.getDifficulties(), parentBeacon.getSolveTimes());
		
		//Update difficulties and solvetimes arrays for next beacon difficulty calculation
		difficulties = parentBeacon.getDifficulties();
		solveTimes = parentBeacon.getSolveTimes();
		
		if(difficulties.size() == 27)
			difficulties.remove(0);
		difficulties.add(difficulty);
		
		if(solveTimes.size() == 27)
			solveTimes.remove(0);
		solveTimes.add((int)(getDate()-parentBeacon.getDate())/1000);
		
		//Change the randomX key to this transaction hash if if it is a multiple of 2048
		if(beaconHeight % 2048 == 0)
			randomX_key = getHash();
		else
			randomX_key = parentBeacon.randomX_key;
		
		/*
		 * Get the randomX key 64 beacons behind and use it for the next beacon
		 * So there is a 64 beacons delay between randomX key change and effectiveness
		 */
		Transaction beacon64old = this;
		for(int i = 0; i < 64; i++) {
			if(beacon64old.isGenesis())
				break;
			beacon64old = beacon64old.getLoaded().getParentBeacon();
		}
		
		practical_randomX_key = beacon64old.getLoaded().randomX_key;
		
		
		//Add this transaction to tips list
		Main.getDAG().childLessTxs.add(this);
		
		for(LoadedTransaction parent : parents) {			
			//Remove parent from tips list if in it
			Main.getDAG().childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(parents.length == 1)
			height = parents[0].getHeight() + 1;
		else
			for(LoadedTransaction parent : parents)
				if(parent.getHeight() > height-1)
					height = parent.getHeight() + 1;
		
		
		setupBeaconBranch();
		
		Main.getDAG().getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	/**
	 * Add this beacon to parent beacon's branch or create a new branch
	 * if it's a fork (parent already has a child)
	 * 
	 * Branchs unable optimization of beacon weight and confirmations count calculation
	 */
	private void setupBeaconBranch() {
		
		if(loadedParentBeacon.getLoaded().loadedChildBeacons.size() == 1) {//transaction is parent's first child, make part of parent's main branch
			BeaconBranch parentMainBranch = loadedParentBeacon.getLoaded().getMainBeaconBranch();
			beaconBranchs.put(parentMainBranch, parentMainBranch.addTx(this));
		} else {
			
			//create branch
			BeaconBranch branch = new BeaconBranch();
			branch.addTx(this);
			beaconBranchs.put(branch, BigInteger.ZERO);
			
			//add branch to parent transactions branchs
			for(Transaction parentChainMember : loadedParentBeacon.getLoaded().getMainBeaconBranch().getMembersBefore(loadedParentBeacon))
				parentChainMember.getLoaded().beaconBranchs.put(branch, BigInteger.ZERO);
		
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
			loadedParentBeacon.getLoaded().chooseNextBeacon();
			return;
		}

		if(!confirmedParents)
			confirmParents();
		
		LoadedTransaction mainChainBeaconChild = null;
		List<LoadedTransaction> lst = new ArrayList<LoadedTransaction>();
		BigInteger pounds = BigInteger.ZERO;
		
		for(Transaction e : loadedChildBeacons){
			LoadedTransaction t = e.getLoaded();
			if(t.mainChainMember)
				mainChainBeaconChild = t;
			
			if(t.getWeight().compareTo(pounds) > 0){
				lst.clear();
				pounds = t.getWeight();
				lst.add(t);
			}
			else if(t.getWeight().compareTo(pounds) == 0)
				lst.add(t);
		}
		
	    if (lst.size() == 1 && !lst.get(0).equals(mainChainBeaconChild)) {
	        
	    	lst.get(0).mainChainMember = true;
	    	
	        for (Transaction e : loadedChildBeacons) {
				LoadedTransaction t = e.getLoaded();
		        if (t.equals(lst.get(0)))
		        	continue; 
		        t.undoChain();
		        t.rejectTx();
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
		
		for(Transaction parent : loadedParents)
				parent.getLoaded().setSettler(this);
		
		for(Transaction conflictingTransaction : conflictualTxs) {
			
			boolean canConfirm = true;
			b:
			for(TxOutput input : conflictingTransaction.getLoaded().loadedInputs) {
				for(Transaction claimer : input.claimers)
					if(!claimer.equals(conflictingTransaction) && conflictualTxs.contains(claimer)) {
						canConfirm = false;
						break b;
					}
			}
			
			if(canConfirm)
				conflictingTransaction.getLoaded().confirmTx();
			else
				conflictingTransaction.getLoaded().rejectTx();
			
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
		
		if(settlingTransaction == null)
			settlingTransaction = tx;
		
		s.add(this);
		
		while(!s.isEmpty()){
			tmp = s.pop();
			
			//if element is from mainchain we don't add it's parents and dont proccess it
			if(tmp.isMainChainMember())
				continue;
			
			for(Transaction e : tmp.getLoadedParents()){
				LoadedTransaction t = e.getLoaded();
				//if beacon transaction we add it to continue walking but don't change it's settlingTransaction
				if(t.isBeaconTransaction()) {
					s.add(t);
					continue;
				}
				
				if(t.settlingTransaction == null){
					t.settlingTransaction = tx;
					s.add(t);
				}
			}
			
			//if beacon transaction don't process it
			if(tmp.isBeaconTransaction())
				continue;
			
			boolean canConfirm = true;
			
			//if any input is already spent refuse this transaction
			for(Transaction inputTx : tmp.loadedInputTxs)
				if(inputTx.getLoaded().getStatus().isRefused() || inputTx.getOutputsMap().get(tmp.getAddress()).isSpent()){
					tmp.rejectTx();
					canConfirm = false;
					break;
				}
			
			if(canConfirm) {//run only if transaction hasn't been refused on last check
				b: for(TxOutput input : tmp.loadedInputs)
					for(Transaction claimer : input.claimers) {
						LoadedTransaction loadedClaimer = claimer.getLoaded();
						if(loadedClaimer != this && !loadedClaimer.getStatus().isRefused()) {
							settlingTransaction.getLoaded().conflictualTxs.add(tmp);
							canConfirm = false;
							break b;
						}
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
		
		for(Transaction parent : loadedParents)
			parent.getLoaded().removeSettler(this);
		
		LoadedTransaction mainChainBeaconChild = null;
		
		for(Transaction childBeacon : loadedChildBeacons) {
			LoadedTransaction loadedBeacon = childBeacon.getLoaded();
			if(loadedBeacon.mainChainMember) {
				mainChainBeaconChild = loadedBeacon;
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
		
		changeStatus(TxStatus.PENDING);
		
		while(!s.isEmpty())
		{
			LoadedTransaction tmp = s.pop();
			
			for(Transaction parent : tmp.loadedParents)
			{
				LoadedTransaction loadedParent = parent.getLoaded();
				if(loadedParent.settlingTransaction != settler)
					continue;
				
				loadedParent.settlingTransaction = null;
				
				loadedParent.changeStatus(TxStatus.PENDING);
				s.add(loadedParent);
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
	            for(Transaction parent : current.getLoadedParents()) {
	            	LoadedTransaction loadedParent = parent.getLoaded();
	            	
		            if(loadedParent.height < target.height)
		                continue;
	            
	                if(loadedParent.height == target.height) {
	                    if(loadedParent == target)
	                        return true;
	                    continue;
	                }
	
	                if(!tab.get(loadedParent.height - target.height - 1).contains(loadedParent)) {
	                    tab.get(loadedParent.height - target.height - 1).add(loadedParent);
	                    stack.push(loadedParent);
	                }
	                
	            }
	    }
        
        return false;
	}
	
	private void changeStatus(TxStatus newStatus) {
		TxStatus formerStatus = status;
		status = newStatus;
		Main.getDAG().getEventListener().notify(new TransactionStatusChangedEvent(this, formerStatus));
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
			for(Transaction claimer : out.claimers)
				claimer.getLoaded().rejectTx();
		
		if(isBeaconTransaction())
			for(Transaction childBeacon : loadedChildBeacons)
				childBeacon.getLoaded().rejectTx();
	}
	
	public void save() {
		Main.getDAG().writer.push(this);
	}
	
	/**
	 * Zawy's modifed Digishield v3 (tempered SMA) difficulty algorithm
	 */
	private BigInteger calcDifficulty(List<BigInteger> targets, List<Integer> solveTimes) {
		
		int T = 30;
		
		BigInteger sumD = BigInteger.valueOf(0);
		double sumST = 0;
		
		for (int solveTime : solveTimes) { 
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
	
	public Transaction[] getLoadedParents() {
		return loadedParents.toArray(new Transaction[loadedParents.size()]);
	}
	
	public Transaction getLoadedParent(int index) {
		return loadedParents.get(index);
	}

	public Transaction getParentBeacon() {
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
				newWeight = newWeight.add(branchEntry.getKey().getFirst().getLoaded().getWeight());
		
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
			return settlingTransaction.getLoaded().confirmationCount();
		
		int confirmations = 0;
		for(Entry<BeaconBranch, BigInteger> branchEntry : beaconBranchs.entrySet())
			if(branchEntry.getKey().equals(getMainBeaconBranch()))
				confirmations += branchEntry.getKey().getBranchConfirmations() - branchEntry.getKey().indexOf(this);
			else
				confirmations += branchEntry.getKey().getFirst().getLoaded().confirmationCount();
		
		return confirmations;
	}
	
	

	public Transaction getSettlingTransaction() {
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
	
	private List<Integer> getSolveTimes(){		
		return new ArrayList<Integer>(solveTimes);
	}
	
	public Sha256Hash getRandomXKey() {
		return practical_randomX_key;
	}
	
	public LoadedTransaction(JSONObject state) {
		
		super(Transaction.fromState(state));
		
		height = state.getInt("height");
		
		if(isBeaconTransaction()) {
						
			JSONArray branchesIDs = state.getJSONArray("branches");
			
			//first retrieve main branch and put in map with it's modifier
			BigInteger mainBranchModifier = new BigInteger(Converter.hexToBytes(state.getString("mainBranchModifier")));
			beaconBranchs.put(Main.getDAG().branches.get(branchesIDs.get(0)), mainBranchModifier);
			
			for(int i = 1; i < branchesIDs.length(); i++)
				beaconBranchs.put(Main.getDAG().branches.get(branchesIDs.get(i)), BigInteger.ZERO);
			
			
			floorWeight = new BigInteger(Converter.hexToBytes(state.getString("floorWeight")));
			beaconHeight = state.getLong("beaconHeight");
			
			JSONArray childBeaconsJSON = state.getJSONArray("childBeacons");
			for(int i = 0; i < childBeaconsJSON.length(); i++)
				loadedChildBeacons.add(Main.getDAG().getTx(new Sha256Hash(childBeaconsJSON.getString(i))));
			
			mainChainMember = state.getBoolean("mainChainMember");
			confirmedParents = state.getBoolean("confirmedParents");
			
			JSONArray conflictualTxsJSON = state.getJSONArray("conflictualTxs");
			for(int i = 0; i < conflictualTxsJSON.length(); i++)
				conflictualTxs.add(Main.getDAG().getTx(new Sha256Hash(conflictualTxsJSON.getString(i))));
			
			JSONArray diffs = state.getJSONArray("diffs");
			for(int i = 0; i < diffs.length(); i++)
				difficulties.add(new BigInteger(Converter.hexToBytes(diffs.getString(i))));
			
			JSONArray times = state.getJSONArray("solveTimes");
			for(int i = 0; i < times.length(); i++)
				solveTimes.add(times.getInt(i));
			
			randomX_key = new Sha256Hash(state.getString("RXKey"));
			practical_randomX_key = new Sha256Hash(state.getString("PrRXKey"));
			
		}else {
			JSONArray inputsIds = state.getJSONArray("inputsIds");
			for(int i = 0; i < inputsIds.length(); i++)
				loadedInputs.add(Main.getDAG().outputs.get(inputsIds.getString(i)));
		}
		
		if(state.has("settlingTransaction"))
			settlingTransaction = Main.getDAG().getTx(new Sha256Hash(state.getString("settlingTransaction")));
		
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
			
			baseJSON.put("mainBranchModifier", Converter.bytesToHex(beaconBranchs.get(getMainBeaconBranch()).toByteArray()));
			
			baseJSON.put("floorWeight", Converter.bytesToHex(floorWeight.toByteArray()));
			baseJSON.put("beaconHeight", beaconHeight);
			
			JSONArray childBeaconsHashes = new JSONArray();
			for(Transaction child : loadedChildBeacons)
				childBeaconsHashes.put(child.getHash().toString());
			baseJSON.put("childBeacons", childBeaconsHashes);
			
			baseJSON.put("mainChainMember", mainChainMember);
			baseJSON.put("confirmedParents", confirmedParents);
			
			JSONArray conflictualHashes = new JSONArray();
			for(Transaction tx : conflictualTxs)
				conflictualHashes.put(tx.getHash().toString());
			baseJSON.put("conflictualTxs", conflictualHashes);
			
			JSONArray diffs = new JSONArray();
			for(BigInteger diff : difficulties)
				diffs.put(Converter.bytesToHex(diff.toByteArray()));
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
		
		if(settlingTransaction != null)
			baseJSON.put("settlingTransaction", settlingTransaction.getHash().toString());
		
		return baseJSON;
	}
	
}
