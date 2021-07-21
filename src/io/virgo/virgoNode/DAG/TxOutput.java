package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

/**
 * Represent a transaction output
 * Basically recipient address, amount and a list of transactions
 * that are claiming this output
 */
public class TxOutput {

	private String uuid;
	
	private Sha256Hash originTx;
	private String address;
	private long amount;
	public List<LoadedTransaction> claimers = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	
	public TxOutput(String address, long amount, Sha256Hash originTx) {
		uuid = UUID.randomUUID().toString();
		
		this.address = address;
		this.amount = amount;
		this.originTx = originTx;
		
		Main.getDAG().outputs.put(uuid, this);
	}
	
	/**
	 * Create a TxOutput from a string
	 * 
	 * @param inputString the string to convert to TxOutput, format: "address,amount" or "address,amount,claimedBy"
	 * @return a new TxOutput
	 * @throws NumberFormatException Given amount is not in hex format
	 * @throws ArithmeticException Given amount is out of range
	 * @throws IllegalArgumentException Can't build a TxOutput from this string
	 */
	public static TxOutput fromString(String inputString, Sha256Hash originTx) throws ArithmeticException, IllegalArgumentException {
		
		String[] outArgs = inputString.split(",");
		
		long value = Converter.hexToDec(outArgs[1]).longValueExact();
		
		if(Miscellaneous.validateAddress(outArgs[0], Main.ADDR_IDENTIFIER) && value > 0)
			return new TxOutput(outArgs[0], value, originTx);
		
		throw new IllegalArgumentException("Can't build a TxOutput from this string.");
	}
	
	public String getUUID() {
		return uuid;
	}
	
	public String toString() {
		return address + "," + Converter.decToHex(BigInteger.valueOf(amount));
	}
	
	public String getAddress() {
		return address;
		
	}
	
	public Sha256Hash getOriginTx() {
		return originTx;
	}
	
	public long getAmount() {
		return amount;
	}

	public boolean isSpent() {
		for(LoadedTransaction claimer : claimers) {
			if(claimer.getStatus().isConfirmed())
				return true;
		}
		return false;
	}

}
