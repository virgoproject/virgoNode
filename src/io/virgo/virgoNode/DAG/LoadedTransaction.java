package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import io.virgo.virgoNode.DAG.Events.TransactionLoadedEvent;
import io.virgo.virgoNode.DAG.Events.TransactionStatusChangedEvent;

/**
 * Object representing a loaded Transaction
 * Extends base transaction
 */
public class LoadedTransaction extends Transaction {
	
	private DAG dag;
	
	//branchs the transaction is part of and it's position in it
	LinkedHashMap<Branch, Integer> partOf = new LinkedHashMap<Branch, Integer>();//part of branch till
	
	LinkedHashMap<BeaconBranch, Long> beaconBranchs = new LinkedHashMap<BeaconBranch, Long>();//branch displacement
	
	public ArrayList<String> childs = new ArrayList<String>();
	
	private ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
	
	private long height = 0;
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<LoadedTransaction> loadedInputTxs = new ArrayList<LoadedTransaction>();
	
	private long inputsValue = 0;
		
	private volatile TxStatus status = TxStatus.PENDING;
	
	//beacon related variables
	private long difficulty = 0;
	private long beaconHeight = 0;
	private LoadedTransaction loadedParentBeacon;
	public ArrayList<LoadedTransaction> loadedChildBeacons = new ArrayList<LoadedTransaction>();
	private boolean mainChainMember = false;
	private boolean confirmedParents = false;
	private ArrayList<LoadedTransaction> conflictualTxs = new ArrayList<LoadedTransaction>();
	
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
			
			//add all branch parent is part of to this transaction branchs list
			//If a banch is already present in the list take the highest position
			//See documentation on virgocoin.io for more informations
			for(Branch branch : parent.partOf.keySet()) {
				if(partOf.containsKey(branch)) {
					if(parent.partOf.get(branch) > partOf.get(branch))
						partOf.put(branch, parent.partOf.get(branch));
				} else {
					partOf.put(branch, parent.partOf.get(branch));
				}
			}
			
			//Remove parent from tips list if in it
			dag.childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(loadedParents.size() == 1)
			height = loadedParents.get(0).getHeight() + 1;
		else
			for(LoadedTransaction parent : loadedParents)
				if(parent.getHeight() > height)
					height = parent.getHeight() + 1;
		
		setupBranch();
		
		
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	//genesis constructor
	public LoadedTransaction(DAG dag, TxOutput[] genesisOutputs) {
		super(genesisOutputs);
		
		this.dag = dag;
		
		status = TxStatus.CONFIRMED;
		
		dag.nodesToCheck.put(0l, this);
		
		Branch branch = new Branch();
		branch.addTx(this);
		
		difficulty = 100000;
		mainChainMember = true;
		dag.childLessBeacons.add(this);
		
		settlingTransaction = this;
		
		BeaconBranch beaconBranch = new BeaconBranch();
		beaconBranch.addTx(this);
		beaconBranchs.put(beaconBranch, 0l);
		
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
		
		difficulty = DAG.calcDifficulty(loadedParentBeacon.getDifficulty(), getDate()-loadedParentBeacon.getDate(), beaconHeight);
		
		//Add this transaction to tips list
		dag.childLessTxs.add(this);
		
		for(LoadedTransaction parent : loadedParents) {
			//add this transaction to parents's child list
			parent.addChild(this);
			
			//add all branch parent is part of to this transaction branchs list
			//If a banch is already present in the list take the highest position
			//See documentation on virgocoin.io for more informations
			for(Branch branch : parent.partOf.keySet()) {
				if(partOf.containsKey(branch)) {
					if(parent.partOf.get(branch) > partOf.get(branch))
						partOf.put(branch, parent.partOf.get(branch));
				} else {
					partOf.put(branch, parent.partOf.get(branch));
				}
			}
			
			//Remove parent from tips list if in it
			dag.childLessTxs.remove(parent);
		}
		
		//determine transaction height (highest parent+1)
		if(loadedParents.size() == 1)
			height = loadedParents.get(0).getHeight() + 1;
		else
			for(LoadedTransaction parent : loadedParents)
				if(parent.getHeight() > height)
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
			beaconBranchs.put(branch, 0l);
			
			//add branch to parent transactions branchs
			for(LoadedTransaction parentChainMember : loadedParentBeacon.getMainBeaconBranch().getMembersBefore(loadedParentBeacon))
				parentChainMember.beaconBranchs.put(branch, 0l);
		
		}
		
		chooseNextBeacon();
	}
	
	/**
	 * Setup branching for this transaction
	 * Branching optimizes weight calculation by forming groups of transaction
	 */
	private void setupBranch() {
		if(loadedParents.size() != 1) {
			//create a branch and add it to parents direct branch with /2 modifier (distibute weight equally between parents)
			Branch branch = new Branch();
			branch.addTx(this);
			
		}else {
			
			LoadedTransaction parent = loadedParents.get(0);
			
			if(parent.childs.size() == 1)//transaction is parent's first child, make part of parent's main branch
				parent.getMainBranch().addTx(this);
			else {
				
				Branch branch = new Branch();
				branch.addTx(this);
				
			}
			
		}
		
	}
	
	private void chooseNextBeacon() {
		if(!mainChainMember)
			loadedParentBeacon.chooseNextBeacon();
		
		if(!confirmedParents)
			confirmParents();
		
		if(loadedChildBeacons.size() == 1) {
			loadedChildBeacons.get(0).mainChainMember = true;
			loadedChildBeacons.get(0).chooseNextBeacon();
		}else {
			
			LoadedTransaction mainChainBeaconChild = null;
			
			for(LoadedTransaction childBeacon : loadedChildBeacons) {
				if(childBeacon.mainChainMember) {
					mainChainBeaconChild = childBeacon;
					break;
				}
			}
			
			if(mainChainBeaconChild == null) {
				
				for(LoadedTransaction childBeacon : loadedChildBeacons)
					if(mainChainBeaconChild == null || mainChainBeaconChild.getWeight() < childBeacon.getWeight())
						mainChainBeaconChild = childBeacon;
				
				
				for(LoadedTransaction childBeacon : loadedChildBeacons)
					if(mainChainBeaconChild != childBeacon && childBeacon.getWeight() == mainChainBeaconChild.getWeight())
						return;
				
				mainChainBeaconChild.mainChainMember = true;
				mainChainBeaconChild.chooseNextBeacon();
				
			} else {
				
				for(LoadedTransaction childBeacon : loadedChildBeacons)
					if(mainChainBeaconChild != childBeacon && childBeacon.getWeight() == mainChainBeaconChild.getWeight()) {
						mainChainBeaconChild.undoChain();
						return;
					}
				
				LoadedTransaction biggestChild = mainChainBeaconChild;
				for(LoadedTransaction childBeacon : loadedChildBeacons)
					if(biggestChild != childBeacon && biggestChild.getWeight() < childBeacon.getWeight())
						biggestChild = childBeacon;
				
				if(biggestChild != mainChainBeaconChild) {
					mainChainBeaconChild.undoChain();
					biggestChild.mainChainMember = true;
					biggestChild.chooseNextBeacon();
				}
			}
			
		}
		
	}
	
	private void confirmParents() {
		confirmedParents = true;
		
		for(LoadedTransaction parent : loadedParents)
			parent.setSettler(this);
		
		for(LoadedTransaction conflictingTransaction : conflictualTxs) {
			
			boolean canConfirm = true;
			b:
			for(TxOutput input : conflictingTransaction.loadedInputs) {
				for(LoadedTransaction claimer : input.claimers)
					if(claimer != this)
						if(claimer.confirmationCount() >= confirmationCount()) {
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
	
	private void setSettler(LoadedTransaction tx) {
		if(settlingTransaction != null)
			return;
		
		settlingTransaction = tx;

		for(LoadedTransaction inputTx : loadedInputTxs)
			if(inputTx.getStatus().isRefused())
				rejectTx();
		
		boolean canConfirm = true;
		for(TxOutput input : loadedInputs)
			if(input.claimers.size() > 1) {
				settlingTransaction.conflictualTxs.add(this);
				break;
			}
				
		if(canConfirm)
			confirmTx();
		
		for(LoadedTransaction parent : loadedParents)
			parent.setSettler(tx);
	}
	
	private void undoChain() {
		if(!mainChainMember)
			return;
		
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
	
	private void removeSettler(LoadedTransaction settler) {
		if(settlingTransaction != settler)
			return;
		
		settlingTransaction = null;
		changeStatus(TxStatus.PENDING);
		
		for(LoadedTransaction parent : loadedParents)
			parent.removeSettler(settler);
		
	}
	
	/**
	 * Checks if this transaction is a direct child of target transaction
	 * For this we verify that this transaction has all parent branchs at a higher or equal branch height
	 * 
	 * @param target The target transaction
	 * @return true if this transaction is a direct child of target, false otherwise
	 */
	public boolean isChildOf(LoadedTransaction target) {
		
		for(Branch branch : target.partOf.keySet())
			if(partOf.containsKey(branch)) {
				if(partOf.get(branch) < target.partOf.get(branch))
					return false;
				
			} else return false;
			
		
		return true;

	}
	
	public void addChild(LoadedTransaction child) {
		childs.add(child.getUid());
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
		
	}
	
	public void save() {
		dag.writer.push(this);
	}
	
	public Branch getMainBranch() {
		return partOf.keySet().iterator().next();
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
	
	public long getHeight() {
		return height;
	}
	
	public long getWeight() {
		long newWeight = 0;

		for(Entry<BeaconBranch, Long> branchEntry : beaconBranchs.entrySet())
			if(branchEntry.getKey().equals(getMainBeaconBranch()))
				newWeight += branchEntry.getKey().getBranchWeight() - branchEntry.getValue();
			else
				newWeight += branchEntry.getKey().getFirst().getWeight();
		
		return newWeight;
	}
	
	public long getBeaconHeight() {
		return beaconHeight;
	}
	
	public int confirmationCount() {
		if(settlingTransaction == null)
			return 0;
		
		if(!isBeaconTransaction())
			return settlingTransaction.confirmationCount();
		
		int confirmations = 0;
		for(Entry<BeaconBranch, Long> branchEntry : beaconBranchs.entrySet())
			if(branchEntry.getKey().equals(getMainBeaconBranch()))
				confirmations += branchEntry.getKey().getBranchConfirmations() - branchEntry.getKey().indexOf(this);
			else
				confirmations += branchEntry.getKey().getFirst().confirmationCount();
		
		return confirmations;
	}

	public long getDifficulty() {
		return difficulty;
	}
	
}
