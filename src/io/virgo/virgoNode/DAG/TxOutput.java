package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

/**
 * Represent a transaction output
 * Basically recipient address, amount and a list of transactions
 * that are claiming this output
 */
public class TxOutput {

	private String uuid;
	
	private Transaction originTx;
	private String address;
	private long amount;
	public List<Transaction> claimers = Collections.synchronizedList(new ArrayList<Transaction>());
	
	public TxOutput(String address, long amount) {
		uuid = UUID.randomUUID().toString();
		
		this.address = address;
		this.amount = amount;
		
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
	public static TxOutput fromString(String inputString) throws ArithmeticException, IllegalArgumentException {
		
		String[] outArgs = inputString.split(",");
		
		long value = Converter.hexToDec(outArgs[1]).longValueExact();
		
		if(Miscellaneous.validateAddress(outArgs[0], Main.ADDR_IDENTIFIER) && value > 0)
			return new TxOutput(outArgs[0], value);
		
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
	
	public void setOriginTx(Transaction transaction) {
		originTx = transaction;
	}
	
	public Transaction getOriginTx() {
		return originTx;
	}
	
	public long getAmount() {
		return amount;
	}

	public boolean isSpent() {
		for(Transaction claimer : claimers) {
			if(claimer.getLoaded().getStatus().isConfirmed())
				return true;
		}
		return false;
	}

}
