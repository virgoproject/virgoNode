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

public class LoadedTransaction extends Transaction {
	
	private DAG dag;
	
	//branchs the transaction is part of and the modifier (ex SUB1000000 or DIV2) applied to calculate weight
	protected LinkedHashMap<Branch, WeightModifier> branchs = new LinkedHashMap<Branch, WeightModifier>();
	HashMap<Branch, Integer> partOf = new HashMap<Branch, Integer>();//part of branch till
	
	public ArrayList<String> childs = new ArrayList<String>();
	
	private ArrayList<LoadedTransaction> loadedParents = new ArrayList<LoadedTransaction>();
	private ArrayList<LoadedTransaction> loadedChilds = new ArrayList<LoadedTransaction>();
	
	private long height = 0;
	private long ceilingValue = 0;//smallest main chain vertex number the tx is parent of
	
	private ArrayList<TxOutput> loadedInputs = new ArrayList<TxOutput>();
	private ArrayList<LoadedTransaction> loadedInputTxs = new ArrayList<LoadedTransaction>();
	
	public ArrayList<LoadedTransaction> loadedOutputClaimers = new ArrayList<LoadedTransaction>();

	private volatile long weight = 0;
	private long selfWeight = 0;
	private volatile long dagHeightOnLastWeightUpdate = 0;
	

	private volatile int stability = 0;
	private volatile long dagHeightOnLastStabUpdate = 0;
	
	
	private long inputsValue = 0;
	
	
	private ParentsOrder parentsOrder = ParentsOrder.NO_ORDER;
	
	private boolean parentsConfirmed = false;//parents = all transaction before self, not only direct parents
	
	private TxStatus status = TxStatus.PENDING;
	
	public LoadedTransaction(DAG dag, Transaction baseTransaction, LoadedTransaction[] parents, LoadedTransaction[] inputTxs) {
		
		super(baseTransaction);
		
		this.dag = dag;
		
		this.loadedParents.addAll(Arrays.asList(parents));
		this.loadedInputTxs.addAll(Arrays.asList(inputTxs));
		
		for(LoadedTransaction inputTx : inputTxs) {
			TxOutput out = inputTx.getOutputsMap().get(getAddress());
			loadedInputs.add(out);
			inputTx.loadedOutputClaimers.add(this);
			inputsValue += out.getAmount();
		}
		
		for(LoadedTransaction parent : loadedParents) {
			parent.addChild(this);
			partOf.putAll(parent.partOf);
		}
		
		if(loadedParents.size() == 1) {
			parentsOrder = ParentsOrder.ORDERED;
			height = loadedParents.get(0).getHeight() + 1;
		} else
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
		
		parentsConfirmed = true;
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
	
	private void setupBranch() {
		if(getParentsOrder().equals(ParentsOrder.NO_ORDER)) {
			//create a branch and add it to parents direct branch with mod DIV2
			Branch branch = new Branch();
			branch.addTx(this);
			branchs.put(branch, new WeightModifier(Modifier.NONE, 0));
			
			WeightModifier modifierForParents = new WeightModifier(Modifier.DIV, 2);
			
			for(LoadedTransaction parent : loadedParents)
				for(LoadedTransaction parentChainMember : parent.getMainBranch().getMembersBefore(parent))
					parentChainMember.branchs.put(branch, modifierForParents);
			
			return;
		}
		
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
		
		dag.nodesToCheck.pollFirstEntry().getValue().checkNode();
		
	}
	
	public boolean isChildOf(LoadedTransaction target) {
		
		for(Branch branch : partOf.keySet()) {
			
			if(target.partOf.containsKey(branch))
				if(branch.positionOf(this) > branch.positionOf(target))
					return true;
			
		}
		
		return false;
		
		/**boolean whatToreturn = false;
		
		if(loadedParents.contains(target))
			whatToreturn = true;
		
		for(LoadedTransaction parent : loadedParents)
			if(parent.isChildOf(target))
				whatToreturn = true;
		
		return false;
		
		/**for(LoadedTransaction parent : loadedParents) {
			if(parent == target)
				return true;
			if(dag.mainChain.contains(parent)) {
				if(target.ceilingValue <= parent.ceilingValue)
					return true;
			} else {
				if((target.ceilingValue != 0 || parent.ceilingValue == 0) && parent.isChildOf(target))
					return true;
			}

		}		
		return false;**/
		/**for(Branch branch : partOf.keySet()) {
			
			if(target.partOf.containsKey(branch))
				if(target.partOf.get(branch) <= partOf.get(branch))
					return true;
			
		}
		return false;**/
	}
	
	public void addChild(LoadedTransaction child) {
		loadedChilds.add(child);
		childs.add(child.getUid());
	}

	/**public void triggerParentMainNode() {
		
		for(LoadedTransaction parent : loadedParents) {
			if(dag.mainChain.contains(parent) && parent.parentsConfirmed) {
				parent.chooseNextMainNode();
				return;
			}
		}
	}**/
	
	public boolean confirmTx() {
		if(status.isPending()) {
			
			for(TxOutput input : loadedInputs) {
				if(!input.usable)
					return false;
			}
			
			changeStatus(TxStatus.CONFIRMED);
			for(TxOutput output : getOutputsMap().values())
				output.usable = true;
			
			return true;
			
		} else {
			return true;
		}
	}
	
	public void rejectTx() {
		changeStatus(TxStatus.REFUSED);
		
		getMainBranch().suppressWeight(this);
	}
	
	public boolean confirmParents(long stayIn) {
		
		boolean parentsConfirmed = true;
		
		for(LoadedTransaction parent : loadedParents) {
			if(parent.ceilingValue >= stayIn) {
				if(!parent.confirmTx())
					parentsConfirmed = false;
				if(!parent.parentsConfirmed && !parent.confirmParents(stayIn))
					parentsConfirmed = false;
			} else if(!parent.parentsConfirmed) {
				parentsConfirmed = false;
			}
		}
		
		this.parentsConfirmed = parentsConfirmed;
		return parentsConfirmed;
	}
	
	private void checkNode() {
		
		if(!parentsConfirmed)
			confirmParents(ceilingValue);
		confirmTx();
		
		if(loadedChilds.size() == 0) {
			dag.nodesToCheck.put(ceilingValue, this);
			return;
		}
		
		if(loadedChilds.size() == 1) {
			dag.mainChain.add(loadedChilds.get(0));
			loadedChilds.get(0).setCeilingAndClaimInputs(ceilingValue+1);
			loadedChilds.get(0).checkNode();
			return;
		} else {
			
			//get child that is MCn
			LoadedTransaction mainChainNodeChild = null;
			
			for(LoadedTransaction child : loadedChilds) {
				if(dag.mainChain.contains(child)) {
					mainChainNodeChild = child;
					break;
				}
			}
			
			if(mainChainNodeChild == null) {
				
				for(LoadedTransaction child : loadedChilds) {
					if(mainChainNodeChild == null || child.getWeight(true) > mainChainNodeChild.getWeight(false))
						mainChainNodeChild = child;
				}
				
			}
			
			for(LoadedTransaction child : loadedChilds) {
				if(child == mainChainNodeChild)
					continue;
				
				if(child.getWeight(true) > mainChainNodeChild.getWeight(false)) {
					if(child.getWeight(true) > mainChainNodeChild.getWeight(true)) {
						
						//undo main chain from here
						for(int i = dag.mainChain.indexOf(this)+1; i < dag.mainChain.size(); i++) {
							LoadedTransaction childMCNode = dag.mainChain.get(i);
							childMCNode.undoMainChain(ceilingValue);
							
							dag.mainChain.remove(i);
						}
						
						dag.mainChain.add(child);
						child.setCeilingAndClaimInputs(ceilingValue+1);
						child.checkNode();
						
					}
				}
				
			}
			
			boolean hasAllTips = true;
			//do another for because mainChainNodeChild could have changed during last for
			for(LoadedTransaction child : loadedChilds) {
				if(child == mainChainNodeChild)
					continue;
				
				for(LoadedTransaction tip : dag.getTips())
					if(tip.isChildOf(child)) {
						if(!tip.isChildOf(mainChainNodeChild)) {
							hasAllTips = false;
							break;
						}
							
					}
			}
			
			if(!hasAllTips) {
				dag.nodesToCheck.put(ceilingValue, this);
				mainChainNodeChild.checkNode();
			}
				
			
		}
		
		
		
	}
	
	/**public void chooseNextMainNode() {
		
		if(!parentsConfirmed)
			confirmParents(ceilingValue);
		confirmTx();
		
		LoadedTransaction nextMainNode = null;
		
		if(loadedChilds.size() == 0)
			return;
		
		for(LoadedTransaction child : loadedChilds) {
			if(nextMainNode == null || child.weight > nextMainNode.weight)
				nextMainNode = child;
		}
		
		if(dag.mainChain.contains(nextMainNode)) {
			nextMainNode.chooseNextMainNode();
			return;
		}
			
		//the current tx is not the latest node of the MC, we need to cancel child nodes
		if(dag.mainChain.indexOf(this) != dag.mainChain.size()-1) {
			
			for(int i = dag.mainChain.indexOf(this)+1; i < dag.mainChain.size(); i++) {
				LoadedTransaction childMCNode = dag.mainChain.get(i);
				childMCNode.undoMainChain(ceilingValue);
				
				dag.mainChain.remove(i);
			}
			
		}
		
		dag.mainChain.add(nextMainNode);
		nextMainNode.setCeilingAndClaimInputs(ceilingValue+1);
	}**/
	
	public void setCeilingAndClaimInputs(long value) {
		if(ceilingValue != 0 || isGenesis())
			return;
		
		ceilingValue = value;
		
		boolean canClaimInputs = true;
		for(TxOutput input : loadedInputs) {
			if(canClaimInputs && input.claimedByLoaded != null) {
				
				if(input.claimedByLoaded.ceilingValue < ceilingValue) {
					canClaimInputs = false;
				} else if(input.claimedByLoaded.ceilingValue == ceilingValue) {
					
					if(isChildOf(input.claimedByLoaded)) {
						canClaimInputs = false;
					}else if(!input.claimedByLoaded.isChildOf(this)) {
						canClaimInputs = false;
						
						input.claimedByLoaded.rejectTx();
 						input.claimedByLoaded = null;
						input.claimedBy = "";
					}
					
				}
			}
		}
		
		if(canClaimInputs) {
			for(TxOutput input : loadedInputs) {
				input.claimedBy = getUid();
				input.claimedByLoaded = this;
			}
		} else {
			rejectTx();
		}
		
		for(LoadedTransaction parent : loadedParents)
			parent.setCeilingAndClaimInputs(value);
		
	}
	
	public void undoMainChain(long minCeilingValue) {
		if(ceilingValue <= minCeilingValue)
			return;
		
		if(status.isRefused()) {//if status = 2 then the transaction hasnt claired it's outputs, we also need to regive weight
			getMainBranch().addWeight(this);
		} else {
			
			for(TxOutput output : getOutputsMap().values()) {
				output.usable = false;
				output.claimedBy = "";
				output.claimedByLoaded = null;
			}	
			
		}
		
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
		for(LoadedTransaction input : loadedInputTxs) {
			if(inputsStability > input.getStability() || inputsStability == -1)
				inputsStability = input.getStability();
		}

		long txweight = getWeight(true);
		
		float ownStab = (float) txweight/(getOutputsValue()-getReturnAmount());
		
		stability = (int) (Math.min(ownStab,1)*inputsStability);
		dagHeightOnLastStabUpdate = dag.loadedTxsCount();
		
		System.out.println(getUid() + " " + txweight + " " + inputsStability + " " + ownStab);
		
		return stability;
	}
	
	public long getTotalInput() {
		return inputsValue;
	}
	
	public ParentsOrder getParentsOrder() {
		return parentsOrder;
	}
	
	public LoadedTransaction[] getLoadedParents() {
		return loadedParents.toArray(new LoadedTransaction[loadedParents.size()]);
	}
	
	public LoadedTransaction getLoadedParent(int index) {
		return loadedParents.get(index);
	}

	public long getWeight(boolean recalculateIfOutdated) {
		if(dagHeightOnLastWeightUpdate == dag.loadedTxsCount() || recalculateIfOutdated == false)
			return weight;
		
		long newWeight = 0;
		
		for(Entry<Branch, WeightModifier> branchEntry : branchs.entrySet()) {
			
			if(branchEntry.getKey().equals(getMainBranch()))
				newWeight += branchEntry.getValue().apply(branchEntry.getKey().getBranchWeight());
			else
				newWeight += branchEntry.getValue().apply(branchEntry.getKey().getFirst().getWeight(true));
		}
		
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
