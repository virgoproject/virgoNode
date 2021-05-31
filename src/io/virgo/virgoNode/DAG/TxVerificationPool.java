package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.randomX.RandomX;
import io.virgo.randomX.RandomX_VM;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.ECDSA;
import io.virgo.virgoCryptoLib.ECDSASignature;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

/**
 * Thread pool charged of verifying transactions before submitting them to the DAG handling thread
 */
public class TxVerificationPool {

	private DAG dag;
	
	private ThreadPoolExecutor pool;
	
	private Sha256Hash currentVmKey;
	private RandomX randomX;
	private RandomX_VM randomX_vm;
	
	public TxVerificationPool(DAG dag) {
		this.dag = dag;
		
		pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()-2));
		pool.setKeepAliveTime(600000, TimeUnit.MILLISECONDS);
		
		randomX = new RandomX.Builder().build();
		
		randomX.init(dag.getGenesis().getRandomXKey().toBytes());
		randomX_vm = randomX.createVM();
		
		currentVmKey = dag.getGenesis().getRandomXKey();
	}
	
	/**
	 * Verifiy and build a beacon Transaction, then send it to dag handler
	 */
	private void initBeaconTx(Sha256Hash txHash, JSONArray parents, JSONArray outputs, Sha256Hash parentBeaconHash, byte[] nonce, long date, boolean saved) {
		
		CleanedTx cleanedTx = cleanTx(parents, new JSONArray(), outputs);
		ArrayList<Sha256Hash> parentsHashes = cleanedTx.parents;
		ArrayList<TxOutput> constructedOutput = cleanedTx.outputs;
		
		if(parentsHashes.isEmpty())
			return;

		//beacon transaction must have only one output
		if(constructedOutput.size() != 1)
			return;
		
		if(parentBeaconHash == null)
			return;
		
		if(constructedOutput.get(0).getAmount() != Main.BEACON_REWARD)
			return;
		
		//Create corresponding Transaction object
		Transaction tx = new Transaction(txHash,
				parentsHashes.toArray(new Sha256Hash[parentsHashes.size()]),
				constructedOutput.toArray(new TxOutput[1]),
				parentBeaconHash, nonce, date, saved);
		
		dag.queue.add(dag.new txTask(tx));
	}
	
	/**
	 * Initiate regular transaction from raw data
	 * Includes checks for data and signature validity 
	 */
	public void initTx(Sha256Hash txHash, byte[] sigBytes, byte[] pubKey, JSONArray parents, JSONArray inputs, JSONArray outputs, long date, boolean saved) throws IllegalArgumentException {		
		//Remove any useless data from transaction, if there is then transaction will be refused
		CleanedTx cleanedTx = cleanTx(parents, inputs, outputs);
		ArrayList<Sha256Hash> parentsHashes = cleanedTx.parents;
		ArrayList<Sha256Hash> inputsHashes = cleanedTx.inputs;
		ArrayList<TxOutput> constructedOutputs = cleanedTx.outputs;
		
		//make sure neither inputs or ouputs are empty
		if(inputsHashes.isEmpty() || constructedOutputs.isEmpty() || parentsHashes.isEmpty())
			return;
		
		//check if signature is valid, bypass this check if transaction is coming from disk			
		if(!saved) {
		
			ECDSA signer = new ECDSA();
			ECDSASignature sig = ECDSASignature.fromByteArray(sigBytes);
			
			if(!signer.Verify(txHash, sig, pubKey))
				return;
			
		}
		
		//Create corresponding Transaction object
		Transaction tx = new Transaction(txHash, pubKey, ECDSASignature.fromByteArray(sigBytes),
				parentsHashes.toArray(new Sha256Hash[parentsHashes.size()]),
				inputsHashes.toArray(new Sha256Hash[inputsHashes.size()]),
				constructedOutputs.toArray(new TxOutput[constructedOutputs.size()]),
				date, saved);
		
		dag.queue.add(dag.new txTask(tx));
	}
	
	/**
	 * Remove useless or invalid data from transaction
	 */
	public CleanedTx cleanTx(JSONArray parents, JSONArray inputs, JSONArray outputs) {
		
		ArrayList<Sha256Hash> cleanedParents = new ArrayList<Sha256Hash>();
		
		for(int i = 0; i < parents.length(); i++) {
			try {
				Sha256Hash parent = new Sha256Hash(parents.getString(i));
				if(!cleanedParents.contains(parent))
					cleanedParents.add(parent);

			}catch(JSONException|IllegalArgumentException e) {}
		}
		
		ArrayList<Sha256Hash> cleanedInputs = new ArrayList<Sha256Hash>();
		
		for(int i = 0; i < inputs.length(); i++) {
			try {
				Sha256Hash input = new Sha256Hash(inputs.getString(i));
				if(!cleanedInputs.contains(input))
					cleanedInputs.add(input);
			}catch(JSONException|IllegalArgumentException e) {}
		}
		
		ArrayList<TxOutput> cleanedOutputs = new ArrayList<TxOutput>();
		
		for(int i = 0; i < outputs.length(); i++) {
			try {
				TxOutput output = TxOutput.fromString(outputs.getString(i), null);				
				cleanedOutputs.add(output);
			}catch(JSONException | ArithmeticException | IllegalArgumentException e) {}
		}
				
		return new CleanedTx(cleanedParents, cleanedInputs, cleanedOutputs);
	}
	
	
	/**
	 * Runnable building and verifying a transaction validity from JSON
	 */
	public class jsonVerificationTask implements Runnable {

		JSONObject txJson;
		boolean saved;
		
		public jsonVerificationTask(JSONObject txJson, boolean saved) {
			this.txJson = txJson;
			this.saved = saved;
			pool.submit(this);
		}
		
		@Override
		public void run() {
			try {
				if(txJson.toString().length() > 2048)//java char takes 2 bytes, limit transaction size to 4kb
					return;
				
				JSONArray parents = txJson.getJSONArray("parents");
				JSONArray outputs = txJson.getJSONArray("outputs");
				long date = txJson.getLong("date");
				
				//transaction is a beacon transaction
				if(txJson.has("parentBeacon")) {
					
					Sha256Hash parentBeacon = new Sha256Hash(txJson.getString("parentBeacon"));
					byte[] nonce = Converter.hexToBytes(txJson.getString("nonce"));
				
					Sha256Hash txHash = Sha256.getDoubleHash(Converter.concatByteArrays((parents.toString() + outputs.toString()).getBytes(),
							parentBeacon.toBytes(), Miscellaneous.longToBytes(date), nonce));
					
					//Ensure transaction isn't processed yet
					if(dag.isLoaded(txHash) || dag.isTxWaiting(txHash))
						return;
					
					System.out.println("received tx");
					
					initBeaconTx(txHash, parents, outputs, parentBeacon, nonce, date, saved);
					
				//regular transaction
				}else {
					byte[] sigBytes = Converter.hexToBytes(txJson.getString("sig"));
					byte[] pubKey = Converter.hexToBytes(txJson.getString("pubKey"));
					JSONArray inputs = txJson.getJSONArray("inputs");
					
					Sha256Hash txHash = Sha256.getDoubleHash(Converter.concatByteArrays(
							(parents.toString() + inputs.toString() + outputs.toString()).getBytes(), pubKey, Miscellaneous.longToBytes(date)));
					
					//Ensure transaction isn't processed yet
					if(dag.isLoaded(txHash) || dag.isTxWaiting(txHash))
						return;
					
					initTx(txHash, sigBytes, pubKey, parents, inputs, outputs, date, saved);
				}				
			}catch(Exception e) {
				return;
			}

		}
		
	}
	
	/**
	 * Check for a transaction validity, if all good send it to DAG thread
	 */
	public class transactionVerificationTask implements Runnable {

		Transaction tx;
		ArrayList<LoadedTransaction> loadedParents;
		ArrayList<LoadedTransaction> loadedInputs;
		
		public transactionVerificationTask(Transaction tx, ArrayList<LoadedTransaction> loadedParents, ArrayList<LoadedTransaction> loadedInputs) {
			this.tx = tx;
			this.loadedParents = loadedParents;
			this.loadedInputs = loadedInputs;
			
			pool.submit(this);
		}
		
		@Override
		public void run() {
			//check if transaction has no useless parent
			if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0))))
				return;
			
			//Make a list of all input we are child of
			ArrayList<LoadedTransaction> childOfInputs = new ArrayList<LoadedTransaction>();
			for(LoadedTransaction parent : loadedParents)
				for(LoadedTransaction input : loadedInputs)
				if(parent.isChildOf(input))
					childOfInputs.add(input);
				
			long totalInputValue = 0;
			
			//check if inputs are valid and calculate inputs total value
			for(LoadedTransaction input : loadedInputs) {
				//check if transaction is child of its input
				if(!childOfInputs.contains(input))
					return;
				
				
				if(!input.getOutputsMap().containsKey(tx.getAddress()))
					return;
				
				
				if(input.getDate() > tx.getDate())
					return;
				
				
				long amount = input.getOutputsMap().get(tx.getAddress()).getAmount();
				totalInputValue += amount;
			}

			//Check if inputs contains enough funds to cover outputs
			if(totalInputValue != tx.getOutputsValue())
				return;
			
			dag.queue.add(dag.new txTask(tx, loadedParents, loadedInputs));
		}
		
	}
	
	/**
	 * Check for beacon transaction validity, if all good send it to DAG thread 
	 */
	public class beaconVerificationTask implements Runnable {

		Transaction tx;
		LoadedTransaction parentBeacon;
		ArrayList<LoadedTransaction> loadedParents;
		
		public beaconVerificationTask(Transaction tx, LoadedTransaction parentBeacon, ArrayList<LoadedTransaction> loadedParents) {
			this.tx = tx;
			this.parentBeacon = parentBeacon;
			this.loadedParents = loadedParents;
			
			pool.submit(this);
		}
		
		@Override
		public void run() {
			//check if transaction has no useless parent
			if(loadedParents.size() > 1 && (loadedParents.get(0).isChildOf(loadedParents.get(1)) || loadedParents.get(1).isChildOf(loadedParents.get(0))))
				return;

			//check if transaction timestamp is superior to last 10 blocks median and inferior to current time + 15 minutes
			ArrayList<Long> timestamps = new ArrayList<Long>();
			LoadedTransaction currentParent = parentBeacon;
			for(int i = 0; i < 10; i++) {
				timestamps.add(currentParent.getDate());
				currentParent = currentParent.getParentBeacon();
				
				//for first 10 blocks
				if(currentParent == null)
					break;
			}
			
			timestamps.sort(null);
			
			long medianTime = timestamps.get(timestamps.size()/2);
			
			if(tx.getDate() < medianTime || tx.getDate() > System.currentTimeMillis()+900000)
				return;
			

			//check if transaction is effectively child of it's parent beacon
			boolean childOf = false;
			for(LoadedTransaction parent : loadedParents)
				if(parent.isChildOf(parentBeacon)) {
					childOf = true;
					break;
				}
					
			if(!childOf)
				return;

			if(!parentBeacon.getRandomXKey().equals(currentVmKey)) {
				randomX.changeKey(parentBeacon.getRandomXKey().toBytes());
				currentVmKey = parentBeacon.getRandomXKey();
			}
			
			byte[] txHash = randomX_vm.getHash(tx.getHash().toBytes());
					
			byte[] hashPadded = new byte[txHash.length + 1];
			for (int i = 0; i < txHash.length; i++) {
				hashPadded[i + 1] = txHash[i];
			}
			
			BigInteger hashValue = new BigInteger(ByteBuffer.wrap(hashPadded).array());
			
			System.out.println("checking hash");
			
			//check if required difficulty is met
			if(hashValue.compareTo(Main.MAX_DIFFICULTY.divide(parentBeacon.getDifficulty())) >= 0)
				return;
			
			System.out.println("ok");
			
			dag.queue.add(dag.new txTask(tx, loadedParents, parentBeacon));
		}
		
	}
	
	/**
	 * Object representing cleaned transaction data
	 */
	public class CleanedTx {
		
		private ArrayList<Sha256Hash> parents;
		private ArrayList<Sha256Hash> inputs;
		private ArrayList<TxOutput> outputs;
		
		public CleanedTx(ArrayList<Sha256Hash> parents, ArrayList<Sha256Hash> inputs, ArrayList<TxOutput> outputs) {
			this.parents = parents;
			this.inputs = inputs;
			this.outputs = outputs;
		}
		
	}
	
}
