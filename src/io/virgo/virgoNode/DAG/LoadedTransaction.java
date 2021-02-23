package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoNode.DAG.Events.TransactionLoadedEvent;
import io.virgo.virgoNode.DAG.Events.TransactionStatusChangedEvent;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.WeightModifier.Modifier;

/**
 * Object representing a loaded Transaction
 * Extends base transaction
 */
public class LoadedTransaction extends Transaction {
	
	private DAG dag;
	
	//branchs the transaction is part of and the modifier (ex. SUB1000000 or DIV2) applied to calculate weight
	protected LinkedHashMap<Branch, WeightModifier> branchs = new LinkedHashMap<Branch, WeightModifier>();
	HashMap<Branch, Integer> partOf = new HashMap<Branch, Integer>();//part of branch till
	
	public ArrayList<String> childs = new ArrayList<String>();
	
	private ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
	private ArrayList<LoadedTransaction> loadedChilds = new ArrayList<LoadedTransaction>();
	
	private long height = 0;
	private long ceilingValue = 0;//smallest main chain vertex number the tx is parent of
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<LoadedTransaction> loadedInputTxs = new ArrayList<LoadedTransaction>();
	
	private volatile long weight = 0;
	private long selfWeight = 0;
	private volatile long dagHeightOnLastWeightUpdate = 0;
	
	private volatile int stability = 0;
	private volatile long dagHeightOnLastStabUpdate = 0;
	
	private long inputsValue = 0;
		
	private volatile TxStatus status = TxStatus.PENDING;
	
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction[] inputTxs) {
		
		super(baseTransaction);
		
		this.dag = dag;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		this.loadedInputTxs.addAll(Arrays.asList(inputTxs));
		
		//calculate inputs value
		for(LoadedTransaction inputTx : inputTxs) {
			TxOutput out = inputTx.getOutputsMap().get(getAddress());
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
			
		
		selfWeight = getTotalInput() - getOutputsValue();
		
		setupBranch();
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
	}
	
	//genesis constructor
	public LoadedTransaction(DAG dag, TxOutput[] genesisOutputs) {
		super(genesisOutputs);
		
		this.dag = dag;
		
		status = TxStatus.CONFIRMED;
		
		dag.mainChain.add(this);
		dag.nodesToCheck.put(0l, this);
		
		stability = 255;
		
		selfWeight = Main.TOTALUNITS;
		
		Branch branch = new Branch();
		branch.addTx(this);
		branchs.put(branch, new WeightModifier(Modifier.NONE, 0));
		
		dag.getEventListener().notify(new TransactionLoadedEvent(this));
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
			branchs.put(branch, new WeightModifier(Modifier.NONE, 0));
			
			WeightModifier modifierForParents = new WeightModifier(Modifier.DIV, 2);
			
			for(LoadedTransaction parent : loadedParents)
				for(LoadedTransaction parentChainMember : parent.getMainBranch().getMembersBefore(parent))
					parentChainMember.branchs.put(branch, modifierForParents);
			
		}else {
			
			LoadedTransaction parent = loadedParents.get(0);
			
			if(parent.childs.size() == 1) {//transaction is parent's first child, make part of parent's main branch
				Branch parentMainBranch = parent.getMainBranch();
				branchs.put(parentMainBranch, new WeightModifier(Modifier.SUB, parentMainBranch.addTx(this)));
			} else {
				
				WeightModifier modifierForAll = new WeightModifier(Modifier.NONE, 0);
				
				//create branch
				Branch branch = new Branch();
				branch.addTx(this);
				branchs.put(branch, modifierForAll);
				
				//add branch to parent transactions branchs
				for(LoadedTransaction parentChainMember : parent.getMainBranch().getMembersBefore(parent))
					parentChainMember.branchs.put(branch, modifierForAll);

				
				if(dag.mainChain.contains(parent))
					dag.nodesToCheck.put(parent.ceilingValue, parent);
				
			}
			
		}
		
		//check oldest mainchain node requiring attention
		dag.nodesToCheck.pollFirstEntry().getValue().checkNode();
		
	}
	
	/**
	 * Checks if this transaction is a direct child of target transaction
	 * For this we veriffy that this transaction has all parent branchs at a higher or equal branch height
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
		loadedChilds.add(child);
		childs.add(child.getUid());
	}
	
	/**
	 * Confirm this transaction if all inputs are free, otherwise reject it
	 */
	public void confirmTx() {
		for(LoadedTransaction inputTx : loadedInputTxs)
			if(inputTx.getStatus().isRefused()) {
				rejectTx();
				return;
			}
				
			
		changeStatus(TxStatus.CONFIRMED);
	}
	
	public void rejectTx() {
		changeStatus(TxStatus.REFUSED);
		
		getMainBranch().suppressWeight(this);
	}
	
	/**
	 * Try to continue mainChain from this transaction
	 */
	private void checkNode() {
		
		//a mainchain node must have atleast, if it's not the case abort and put back this in nodes to check list
		if(loadedChilds.size() == 0) {
			dag.nodesToCheck.put(ceilingValue, this);
			return;
		}
		
		//if this node has only one child claim inputs from it, add it to mainchain and checkNode from it
		if(loadedChilds.size() == 1) {
			dag.mainChain.add(loadedChilds.get(0));
			loadedChilds.get(0).setCeilingAndClaimInputs(ceilingValue+1);
			loadedChilds.get(0).checkNode();
			return;
		} else {
			//more than one child
			
			//get the child that is part of mainchain
			LoadedTransaction mainChainNodeChild = null;
			
			for(LoadedTransaction child : loadedChilds) {
				if(dag.mainChain.contains(child)) {
					mainChainNodeChild = child;
					break;
				}
			}
			
			if(mainChainNodeChild == null) {
				//can't happen ?
				for(LoadedTransaction child : loadedChilds) {
					if(mainChainNodeChild == null || child.getWeight(true, true) > mainChainNodeChild.getWeight(false))
						mainChainNodeChild = child;
				}
				
			}else {
				
				//check if one of the child has more weight than mainchain child
				for(LoadedTransaction child : loadedChilds) {
					if(child == mainChainNodeChild)
						continue;
					
					if(child.getWeight(true, true) > mainChainNodeChild.getWeight(false)) {
						if(child.getWeight(false) > mainChainNodeChild.getWeight(true, true)) {
							//if so undo main chain from here
							for(int i = dag.mainChain.indexOf(this)+1; i < dag.mainChain.size(); i++) {
								LoadedTransaction childMCNode = dag.mainChain.get(i);
								childMCNode.undoMainChain(ceilingValue);
								
								dag.mainChain.remove(i);
							}
							
							//and add the bigger child to mainchain
							dag.mainChain.add(child);
							child.setCeilingAndClaimInputs(ceilingValue+1);
							child.checkNode();
							return;
						}
					}
					
				}
				
			}
			
			//check if all tips are child of this transaction
			boolean hasAllTips = true;
			//do another for because mainChainNodeChild could have changed during last for
			outer:
			for(LoadedTransaction child : loadedChilds) {
				if(child == mainChainNodeChild)
					continue;
				
				for(LoadedTransaction tip : dag.getTips())
					if(tip.equals(child) || tip.isChildOf(child))
						if(!tip.isChildOf(mainChainNodeChild)) {
							hasAllTips = false;
							break outer;
						}
				
			}
			
			//if not potential confilct is still not resolved, continue to check this node
			if(!hasAllTips)
				dag.nodesToCheck.put(ceilingValue, this);
			
			//check next node
			mainChainNodeChild.checkNode();
		}
		
		
		
	}
	
	/**
	 * Attribute given ceiling value to this transaction and recursively to it's parents if none attributed yet
	 * Also try to claim inputs and confirm his transaction
	 */
	public void setCeilingAndClaimInputs(long value) {
		//if already has a ceiling or is genesis stop here
		if(ceilingValue != 0 || isGenesis())
			return;
		
		//execute this function on parents
		for(LoadedTransaction parent : loadedParents)
			parent.setCeilingAndClaimInputs(value);
		
		//set ceiling
		ceilingValue = value;
		
		
		boolean canClaimInputs = true;
		for(TxOutput input : loadedInputs) {
			//if input is already claimed
			if(canClaimInputs && input.claimedByLoaded != null) {
				
				//can't claim if input claimer is older than us
				if(input.claimedByLoaded.ceilingValue < ceilingValue) {
					canClaimInputs = false;
				
				//if claimer has same ceiling as this transaction
				} else if(input.claimedByLoaded.ceilingValue == ceilingValue) {
					
					//if we are a child of claimer we can't claim
					if(isChildOf(input.claimedByLoaded)) {
						canClaimInputs = false;
					//if claimer is not our child unclaim, we neither claim
					}else if(!input.claimedByLoaded.isChildOf(this)) {
						canClaimInputs = false;
						
						input.claimedByLoaded.rejectTx();
 						input.claimedByLoaded = null;
					}
					
				}
			}
		}
		
		//if all inputs are free claim them and confirm transaction, otherwise reject
		if(canClaimInputs) {
			for(TxOutput input : loadedInputs)
				input.claimedByLoaded = this;
			
			confirmTx();
			
		} else rejectTx();
		
		
	}
	
	public void undoMainChain(long minCeilingValue) {
		if(ceilingValue <= minCeilingValue)
			return;
		
		if(status.isRefused()) {//if status = 2 then the transaction hasnt claired it's outputs, we also need to regive weight
			getMainBranch().addWeight(this);
		} else
			for(TxOutput input : loadedInputs)
				if(input.claimedByLoaded == this)
					input.claimedByLoaded = null;
			
		changeStatus(TxStatus.PENDING);
		
		this.ceilingValue = 0;
		
		for(LoadedTransaction parent : loadedParents)
			parent.undoMainChain(minCeilingValue);
	}
	
	private void changeStatus(TxStatus newStatus) {
		TxStatus formerStatus = status;
		status = newStatus;
		dag.getEventListener().notify(new TransactionStatusChangedEvent(this, formerStatus));
	}
	
	public void save() {
		dag.writer.push(this);
	}
	
	public JSONObject getResumeJSON() {
		JSONObject resume = new JSONObject();
		resume.put("status", getStatus().getCode());
		JSONArray inputs = new JSONArray();
		
		for(TxOutput input : loadedInputs) {
			JSONArray inputJSON = new JSONArray();
			inputJSON.put(input.getOriginTx());
			inputJSON.put(input.getAmount());
			inputs.put(inputJSON);
		}
		resume.put("inputs", inputs);
		
		return resume;
	}
	
	//getters
	public TxStatus getStatus() {
		return status;
	}
	
	public int getStability() {
		if(dagHeightOnLastStabUpdate == dag.loadedTxsCount() || (stability == 255 && dag.nodesToCheck.firstEntry().getValue().ceilingValue > ceilingValue) || isGenesis())
			return stability;
		
		int inputsStability = -1;
		for(LoadedTransaction input : loadedInputTxs)
			if(inputsStability > input.getStability() || inputsStability == -1)
				inputsStability = input.getStability();

		long txweight = getWeight(true);
		
		float ownStab = (float) txweight/(getOutputsValue()-getReturnAmount());
		
		stability = (int) (Math.min(ownStab,1)*inputsStability);
		dagHeightOnLastStabUpdate = dag.loadedTxsCount();
		
		return stability;
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
	
	public long getWeight(boolean recalculateIfOutdated) {
		return getWeight(recalculateIfOutdated, false);
	}

	public long getWeight(boolean recalculateIfOutdated, boolean forceUpdate) {
		if(!forceUpdate && (dagHeightOnLastWeightUpdate == dag.loadedTxsCount() || recalculateIfOutdated == false))
			return weight;
		
		long newWeight = 0;

		for(Entry<Branch, WeightModifier> branchEntry : branchs.entrySet())
			if(branchEntry.getKey().equals(getMainBranch()))
				newWeight += branchEntry.getValue().apply(branchEntry.getKey().getBranchWeight());
			else
				newWeight += branchEntry.getValue().apply(branchEntry.getKey().getFirst().getWeight(true, forceUpdate));
		
		weight = newWeight;
		dagHeightOnLastWeightUpdate = dag.loadedTxsCount();
		
		return weight;
	}

	public long getHeight() {
		return height;
	}

	public long getSelfWeight() {
		return selfWeight;
	}
	
	public Branch getMainBranch() {
		return branchs.keySet().iterator().next();
	}
	
}
