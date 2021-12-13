package io.virgo.virgoNode.DAG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	
	private Sha256Hash hash;
	private String address;
	private ECDSASignature signature = null;
	private byte[] pubKey = null;
	
	private boolean isGenesis = false;
	
	private Sha256Hash[] parentsHashes;
	private Sha256Hash[] inputsHashes;
	
	private LinkedHashMap<String, TxOutput> outputs;
	
	private long date;
	
	private long outputsValue = 0;
	
	//beacon transaction related variables
	private Sha256Hash parentBeaconHash = null;
	private byte[] nonce = null;
	
	private boolean isSaved;
	
	LoadedTransaction loadedTx = null;
	ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	
	/**
	 * Basic transaction constructor
	 */
	public Transaction(Sha256Hash hash, byte[] pubKey, ECDSASignature signature, Sha256Hash[] parentsHashes, Sha256Hash[] inputsHashes, TxOutput[] outputs, long date, boolean isSaved) {
		
		this.hash = hash;
		address = Converter.Addressify(pubKey, Main.ADDR_IDENTIFIER);
		
		this.pubKey = pubKey;
		this.signature = signature;
		this.parentsHashes = parentsHashes;
		this.inputsHashes = inputsHashes;
		this.isSaved = isSaved;
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		this.date = date;
		
		//calculate outputs sum
		for(TxOutput out : outputs) {
			out.setOriginTx(this);
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
	}
	
	/**
	 * Beacon transaction constructor
	 */
	public Transaction(Sha256Hash hash, Sha256Hash[] parentsHashes, TxOutput[] outputs, Sha256Hash parentBeaconHash, byte[] nonce, long date, boolean isSaved) {
		
		this.hash = hash;
		address = outputs[0].getAddress();
		
		this.pubKey = null;
		this.signature = null;
		this.parentsHashes = parentsHashes;
		this.isSaved = isSaved;
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		this.parentBeaconHash = parentBeaconHash;
		this.nonce = nonce;
		
		this.date = date;
		
		//calculate outputs sum
		for(TxOutput out : outputs) {
			out.setOriginTx(this);
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
	}
	
	/**
	 * genesis constructor
	 */
	public Transaction(TxOutput[] outputs) {
		hash = new Sha256Hash("025a6f04e7047b713aaba7fc5003c8266302918c25d1526507becad795b01f3a");
		address = "";
		
		this.outputs = new LinkedHashMap<String, TxOutput>();
		
		for(TxOutput out : outputs) {
			out.setOriginTx(this);
			this.outputs.put(out.getAddress(), out);
			outputsValue += out.getAmount();
		}
		
		date = 0;
		
		isGenesis = true;
		
		parentBeaconHash = null;
		nonce = null;
		
	}
	
	public Transaction(Transaction baseTransaction) {
		this.pubKey = baseTransaction.getPublicKey();
		this.signature = baseTransaction.getSignature();
		this.parentsHashes = baseTransaction.getParentsHashes();
		this.inputsHashes = baseTransaction.getInputsHashes();
		this.isSaved = baseTransaction.isSaved();
		this.outputs = baseTransaction.getOutputsMap();
		this.date = baseTransaction.getDate();
		this.outputsValue = baseTransaction.getOutputsValue();
		this.hash = baseTransaction.getHash();
		this.address = baseTransaction.getAddress();
		this.isGenesis = baseTransaction.isGenesis();
		this.parentBeaconHash = baseTransaction.getParentBeaconHash();
		this.nonce = baseTransaction.getNonce();
		this.loadedTx = baseTransaction.loadedTx;
		
		for(TxOutput out : outputs.values())
			out.setOriginTx(this);
		
	}


	public Sha256Hash getHash() {
		return hash;
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
		return parentBeaconHash != null;
	}

	public long getOutputsValue() {
		return outputsValue;
	}
	
	public Sha256Hash[] getParentsHashes() {
		return parentsHashes;
	}
	
	public ArrayList<String> getParentsHashesStrings() {
		ArrayList<String> hashes = new ArrayList<String>();
		for(Sha256Hash parentHash : parentsHashes)
			hashes.add(parentHash.toString());
		
		return hashes;
	}
	
	public Sha256Hash[] getInputsHashes() {
		return inputsHashes;
	}
	
	public ArrayList<String> getInputsHashesStrings() {
		ArrayList<String> hashes = new ArrayList<String>();
		for(Sha256Hash inputHash : inputsHashes)
			hashes.add(inputHash.toString());
		
		return hashes;
	}
	
	public Sha256Hash getParentBeaconHash() {
		return parentBeaconHash;
	}
	
	public String getParentBeaconHashString() {
		if(parentBeaconHash != null)
			return parentBeaconHash.toString();
		return null;
	}
	
	public byte[] getNonce() {
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
		txJson.put("parents", new JSONArray(getParentsHashesStrings()));
		
		if(!isBeaconTransaction()) {
			txJson.put("sig", getSignature().toHexString());
			txJson.put("pubKey", Converter.bytesToHex(getPublicKey()));
			txJson.put("inputs", new JSONArray(getInputsHashesStrings()));
		} else {
			txJson.put("parentBeacon", getParentBeaconHashString());
			txJson.put("nonce", Converter.bytesToHex(getNonce()));
		}
		
		JSONArray outputsJson = new JSONArray();
		for(Map.Entry<String, TxOutput> entry : getOutputsMap().entrySet())
		   outputsJson.put(entry.getValue().toString());
		txJson.put("outputs", outputsJson);
		
		txJson.put("date", getDate());
		
		return txJson;
	}
	
	public LoadedTransaction getLoaded() {
		lock.readLock().lock();
		LoadedTransaction tx = null;
		try {
			if(loadedTx == null)
				new LoadedTransaction(Main.getDatabase().getState(getHash()), this);
			
			loadedTx.lastAccessed = System.currentTimeMillis();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
			tx = loadedTx;
			lock.readLock().unlock();
		}
		
		return tx;
	}
	
	public LoadedTransaction getLoadedWrite() {
		lock.writeLock().lock();
		try {
			if(loadedTx == null)
				new LoadedTransaction(Main.getDatabase().getState(getHash()), this);
			
			loadedTx.lastAccessed = System.currentTimeMillis();
			
		} catch (SQLException e) {
			//fatal
			lock.writeLock().unlock();
		}
		
		return loadedTx;
	}
	
	public void releaseWriteLock() {
		int c = lock.getWriteHoldCount();
		for(int i = 0; i < c; i++)
			lock.writeLock().unlock();
	}
	
}
