package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;
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
	
	public ArrayList<Sha256Hash> childs = new ArrayList<Sha256Hash>();
	
	private ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
	
	private int height = 0;
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<LoadedTransaction> loadedInputTxs = new ArrayList<LoadedTransaction>();
	
	private long inputsValue = 0;
		
	private volatile TxStatus status = TxStatus.PENDING;
	
	//beacon related variables
	private BigInteger difficulty;
	private long beaconHeight;
	private LoadedTransaction loadedParentBeacon;
	public ArrayList<LoadedTransaction> loadedChildBeacons = new ArrayList<LoadedTransaction>();
	private boolean mainChainMember = false;
	private boolean confirmedParents = false;
	private ArrayList<LoadedTransaction> conflictualTxs = new ArrayList<LoadedTransaction>();
	private List<Long> solveTimes = Collections.synchronizedList(new ArrayList<Long>());//solveTimes of the last 22 parent blocks
	private List<BigInteger> difficulties = Collections.synchronizedList(new ArrayList<BigInteger>());//difficulties of the last 22 parent blocks
	private String randomX_key = null;
	private String practical_randomX_key = null;
	
	private LoadedTransaction settlingTransaction;
	
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction[] inputTxs) {
		
		super(baseTransaction);
		
		this.dag = dag;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		this.loadedInputTxs.addAll(Arrays.asList(inputTxs));
		
		//calculate inputs value
		for(LoadedTransaction inputTx : inputTxs) {
			TxOutput out = inputTx.getOutputsMap().get(getAddress());
			out.claimers.add(this);
			loadedInputs.add(out);
			inputsValue += out.getAmount();
		}
		
		//Add this transaction to tips list
		dag.childLessTxs.add(this);
		
		for(LoadedTransaction parent : loadedParents) {
			//add this transaction to parents's child list
			parent.addChild(this);
			
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
	
	//genesis constructor
	public LoadedTransaction(DAG dag, TxOutput[] genesisOutputs) {
		super(genesisOutputs);
		
		this.dag = dag;
		
		status = TxStatus.CONFIRMED;
		
		difficulty = BigInteger.valueOf(10000);
		mainChainMember = true;
		confirmedParents = true;
		dag.childLessBeacons.add(this);
		
		randomX_key = getHash().toString();
		practical_randomX_key = getHash().toString();
		
		for(int i = 0; i < 22; i++) {
			difficulties.add(difficulty);
			solveTimes.add(60l);
		}
		
		settlingTransaction = this;
		
		BeaconBranch beaconBranch = new BeaconBranch();
		beaconBranch.addTx(this);
		beaconBranchs.put(beaconBranch, BigInteger.ZERO);
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction parentBeacon) {
		super(baseTransaction);
		
		this.dag = dag;
		
		settlingTransaction = this;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		
		this.loadedParentBeacon = parentBeacon;
		loadedParentBeacon.loadedChildBeacons.add(this);
		
		beaconHeight = loadedParentBeacon.getBeaconHeight() + 1;
		
		dag.childLessBeacons.remove(loadedParentBeacon);
		dag.childLessBeacons.add(this);
		
		difficulty = DAG.calcDifficulty(loadedParentBeacon.getDifficulties(), loadedParentBeacon.getSolveTimes());
		
		synchronized(difficulties) {
			synchronized(solveTimes) {
				difficulties = loadedParentBeacon.getDifficulties();
				solveTimes = loadedParentBeacon.getSolveTimes();
				
				if(difficulties.size() == 22)
					difficulties.remove(0);
				difficulties.add(difficulty);
				
				if(solveTimes.size() == 22)
					solveTimes.remove(0);
				solveTimes.add((getDate()-loadedParentBeacon.getDate())/1000);
			}
		}

		
		if(beaconHeight % 2048 == 0)
			randomX_key = getHash().toString();
		else
			randomX_key = parentBeacon.randomX_key;
		
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
			//add this transaction to parents's child list
			parent.addChild(this);
			
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
	
	private void setupBeaconBranch() {
		
		if(loadedParentBeacon.childs.size() == 1) {//transaction is parent's first child, make part of parent's main branch
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
		
	    if (lst.size() == 1 && !((LoadedTransaction)lst.get(0)).equals(mainChainBeaconChild)) {
	        
	    	lst.get(0).mainChainMember = true;
	    	
	        for (LoadedTransaction e : this.loadedChildBeacons) {
		          if (e.equals(lst.get(0)))
		            continue; 
		          e.undoChain();
		          e.rejectTx();
		    } 
	    	
	        lst.get(0).chooseNextBeacon();
	        
	     }
		
	}
	
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
					if(claimer != conflictingTransaction &&
					claimer.confirmationCount() >= conflictingTransaction.confirmationCount())
						canConfirm = false;
						break b;
			}
			
			if(canConfirm)
				conflictingTransaction.confirmTx();
			else
				conflictingTransaction.rejectTx();
			
		}
		
	}
	
	private void setSettler(LoadedTransaction tx) {
	    if (settlingTransaction != null)
	        return; 
		
		Stack<LoadedTransaction> s = new Stack<LoadedTransaction>();
		LoadedTransaction tmp;
		settlingTransaction = tx;
		
		s.add(this);
		
		while(!s.isEmpty()){
			tmp = s.pop();
			
			for(LoadedTransaction inputTx : tmp.loadedInputTxs)
				if(inputTx.getStatus().isRefused() || inputTx.getOutputsMap().get(getAddress()).isSpent()){
					tmp.rejectTx();
					break;
				}
			
			boolean canConfirm = true;
			for(TxOutput input : tmp.loadedInputs)
				for(LoadedTransaction claimer : input.claimers)
					if(claimer != this && !claimer.getStatus().isRefused()) {
						settlingTransaction.conflictualTxs.add(tmp);
						canConfirm = false;
						break;
					}
			if(canConfirm)
				tmp.confirmTx();
			
			
			for(LoadedTransaction e : tmp.getLoadedParents()){
				if(e.settlingTransaction == null){
					e.settlingTransaction = tx;
					s.add(e);
				}
			}
		}
	}
	
	private void undoChain() {
		if(!mainChainMember)
			return;
		
		changeStatus(TxStatus.PENDING);
		mainChainMember = false;
		confirmedParents = false;
		conflictualTxs.clear();
		
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
	
	// iterative version
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
			
			for(LoadedTransaction parent : tmp.loadedParents)
			{
				if(parent.settlingTransaction != settler)
					continue;
				
				parent.settlingTransaction = null;
				parent.changeStatus(TxStatus.PENDING);
				s.add(parent);
			}
		}
	}
	
	/**
	 * Checks if this transaction is a direct child of target transaction
	 * For this we verify that this transaction has all parent branchs at a higher or equal branch height
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
	
	synchronized public void addChild(LoadedTransaction child) {
		childs.add(child.getHash());
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
	
	private List<BigInteger> getDifficulties(){	
		return new ArrayList<BigInteger>(difficulties);
	}
	
	private List<Long> getSolveTimes(){		
		return new ArrayList<Long>(solveTimes);
	}
	
	public String getRandomXKey() {
		return practical_randomX_key;
	}
	
}
