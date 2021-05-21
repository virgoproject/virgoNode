package io.virgo.virgoNode.DAG;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.ECDSASignature;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;

/**
 * Base transaction object
 * Independent of ledger state
 */
public class Transaction {
	
	private Sha256Hash uid;
	private String address;
	private ECDSASignature signature = null;
	private byte[] pubKey = null;
	
	private boolean isGenesis = false;
	
	private Sha256Hash[] parentsUid;
	private Sha256Hash[] inputsUid;
	
	private LinkedHashMap<String, TxOutput> outputs;
	
	private long date;
	
	private long outputsValue = 0;
	private long returnAmount = 0;
	
	//beacon transaction related variables
	private Sha256Hash parentBeaconUid = null;
	private String nonce = "";
	
	private boolean isSaved;
	
	public Transaction(Sha256Hash uid, byte[] pubKey, ECDSASignature signature, Sha256Hash[] parentsUid, Sha256Hash[] inputsUid, TxOutput[] outputs, long date, boolean isSaved) {
		
		address = Converter.Addressify(pubKey, Main.ADDR_IDENTIFIER);
		
		this.pubKey = pubKey;
		this.signature = signature;
		this.parentsUid = parentsUid;
		this.inputsUid = inputsUid;
		this.isSaved = isSaved;
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		this.date = date;
		
		for(TxOutput out : outputs) {
			this.outputs.put(out.getAddress(), out);
			if(out.getAddress().equals(address))
				returnAmount += out.getAmount();
			
				
			outputsValue += out.getAmount();
		}
		
	}
	
	public Transaction(Sha256Hash uid, Sha256Hash[] parentsUid, TxOutput[] outputs, Sha256Hash parentBeaconUid, String nonce, long date, boolean isSaved) {
		
		this.uid = uid;
		address = outputs[0].getAddress();
		
		this.pubKey = null;
		this.signature = null;
		this.parentsUid = parentsUid;
		this.isSaved = isSaved;
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		this.parentBeaconUid = parentBeaconUid;
		this.nonce = nonce;
		
		this.date = date;
		
		for(TxOutput out : outputs) {
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
	}
	
	/*
	 * genesis constructor
	 */
	public Transaction(TxOutput[] outputs) {
		uid = new Sha256Hash("025a6f04e7047b713aaba7fc5003c8266302918c25d1526507becad795b01f3a");
		address = "";
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		for(TxOutput out : outputs) {
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
		date = 0;
		
		isGenesis = true;
		returnAmount = 0;
		
		parentBeaconUid = null;
		nonce = "";
		
	}
	
	public Transaction(Transaction baseTransaction) {
		this.pubKey = baseTransaction.getPublicKey();
		this.signature = baseTransaction.getSignature();
		this.parentsUid = baseTransaction.getParentsUids();
		this.inputsUid = baseTransaction.getInputsUids();
		this.isSaved = baseTransaction.isSaved();
		this.outputs = baseTransaction.getOutputsMap();
		this.date = baseTransaction.getDate();
		this.outputsValue = baseTransaction.getOutputsValue();
		this.returnAmount = baseTransaction.getReturnAmount();
		this.uid = baseTransaction.getUid();
		this.address = baseTransaction.getAddress();
		this.isGenesis = baseTransaction.isGenesis();
		this.parentBeaconUid = baseTransaction.getParentBeaconUid();
		this.nonce = baseTransaction.getNonce();
	}


	public Sha256Hash getUid() {
		return uid;
	}
	
	public String getAddress() {
		return address;
	}
	
	public boolean isGenesis() {
		return isGenesis;
	}
	
	public ECDSASignature getSignature() {
		return signature;
	}
	
	public byte[] getPublicKey() {
		return pubKey;
	}
	
	public boolean isSaved() {
		return isSaved;
	}
	
	public boolean isBeaconTransaction() {
		return parentBeaconUid != null;
	}
	
	public long getReturnAmount() {
		return returnAmount;
	}
	
	public long getOutputsValue() {
		return outputsValue;
	}
	
	public Sha256Hash[] getParentsHashes() {
		return parentsUid;
	}
	
	public ArrayList<String> getParentsHashesStrings() {
		ArrayList<String> hashes = new ArrayList<String>();
		for(Sha256Hash parentHash : parentsUid)
			hashes.add(parentHash.toString());
		
		return hashes;
	}
	
	public Sha256Hash[] getInputsHashes() {
		return inputsUid;
	}
	
	public ArrayList<String> getInputsHashesStrings() {
		ArrayList<String> hashes = new ArrayList<String>();
		for(Sha256Hash inputHash : inputsUid)
			hashes.add(inputHash.toString());
		
		return hashes;
	}
	
	public Sha256Hash getParentBeaconHash() {
		return parentBeaconUid;
	}
	
	
	
	
	public String getNonce() {
		return nonce;
	}
	
	public LinkedHashMap<String, TxOutput> getOutputsMap() {
		return new LinkedHashMap<String, TxOutput>(outputs);
	}

	public long getDate() {
		return date;
	}
	
	/**
	 * @return JSON representation of the transaction
	 */
	public JSONObject toJSONObject() {
		JSONObject txJson = new JSONObject();
		txJson.put("parents", new JSONArray(getParentsUids()));
		
		if(!isBeaconTransaction()) {
			txJson.put("sig", getSignature().toHexString());
			txJson.put("pubKey", Converter.bytesToHex(getPublicKey()));
			txJson.put("inputs", new JSONArray(getInputsUids()));
		} else {
			txJson.put("parentBeacon", getParentBeaconUid());
			txJson.put("nonce", getNonce());
		}
		
		JSONArray outputsJson = new JSONArray();
		for(Map.Entry<String, TxOutput> entry : getOutputsMap().entrySet())
		   outputsJson.put(entry.getValue().toString());
		txJson.put("outputs", outputsJson);
		
		txJson.put("date", getDate());
		
		return txJson;
	}
	
}
