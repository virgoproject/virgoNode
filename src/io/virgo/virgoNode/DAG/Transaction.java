package io.virgo.virgoNode.DAG;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.ECDSASignature;
import io.virgo.virgoNode.Main;

/**
 * Base transaction object, 'raw' data only
 */
public class Transaction {
	
	private String uid;
	private String address;
	private ECDSASignature signature;
	private byte[] pubKey;
	
	private boolean isGenesis = false;
	
	private String[] parentsUid;
	private String[] inputsUid;
	
	private LinkedHashMap<String, TxOutput> outputs;
	
	private long date;
	
	private long outputsValue = 0;
	private long returnAmount = 0;
	
	private boolean isSaved;
	
	public Transaction(byte[] pubKey, ECDSASignature signature, String[] parentsUid, String[] inputsUid, TxOutput[] outputs, long date, boolean isSaved) {
		
		uid = Converter.Addressify(signature.toByteArray(), Main.TX_IDENTIFIER);
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
	
	/*
	 * genesis constructor
	 */
	public Transaction(TxOutput[] outputs) {
		uid = Converter.Addressify("genesis".getBytes(), Main.TX_IDENTIFIER);
		address = "";
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		for(TxOutput out : outputs) {
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
		date = 0;
		
		isGenesis = true;
		returnAmount = 0;
		
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
	}


	public String getUid() {
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
	
	public long getReturnAmount() {
		return returnAmount;
	}
	
	public long getOutputsValue() {
		return outputsValue;
	}
	
	public String[] getParentsUids() {
		return parentsUid;
	}
	
	public String[] getInputsUids() {
		return inputsUid;
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
		txJson.put("sig", getSignature().toHexString());
		txJson.put("pubKey", Converter.bytesToHex(getPublicKey()));
		txJson.put("parents", new JSONArray(getParentsUids()));
		txJson.put("inputs", new JSONArray(getInputsUids()));
		
		JSONArray outputsJson = new JSONArray();
		for(Map.Entry<String, TxOutput> entry : getOutputsMap().entrySet())
		   outputsJson.put(entry.getValue().toString());
		txJson.put("outputs", outputsJson);
		
		txJson.put("date", getDate());
		
		return txJson;
	}
	
}
