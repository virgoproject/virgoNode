package io.virgo.virgoNode.DAG;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.Utils.Miscellaneous;

public class TxOutput {

	private String originTx;
	private String address;
	private long amount;
	public List<LoadedTransaction> claimers = Collections.synchronizedList(new ArrayList<LoadedTransaction>());
	
	public TxOutput(String address, long amount, String originTx) {
		this.address = address;
		this.amount = amount;
		this.originTx = originTx;
	}
	
	public TxOutput(String address, long amount, String originTx, String claimedBy) {
		this.address = address;
		this.amount = amount;
		this.originTx = originTx;
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
	public static TxOutput fromString(String inputString, String originTx) throws ArithmeticException, IllegalArgumentException {
		
		String[] outArgs = inputString.split(",");
		
		switch(outArgs.length) {
		case 2:
			if(Miscellaneous.validateAddress(outArgs[0], Main.ADDR_IDENTIFIER))
				return new TxOutput(outArgs[0], Converter.hexToDec(outArgs[1]).longValueExact(), originTx);
			break;
		case 3:
			if(Miscellaneous.validateAddress(outArgs[0], Main.ADDR_IDENTIFIER) && Miscellaneous.validateAddress(outArgs[2], Main.TX_IDENTIFIER))
				return new TxOutput(outArgs[0], Converter.hexToDec(outArgs[1]).longValueExact(), originTx, outArgs[3]);
		}
		
		throw new IllegalArgumentException("Can't build a TxOutput from this string.");
	}
	
	public String toString() {
		return address + "," + Converter.decToHex(BigInteger.valueOf(amount));
	}
	
	public String getAddress() {
		return address;
		
	}
	
	public String getOriginTx() {
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
