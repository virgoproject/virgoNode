package io.virgo.virgoNode.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;

import io.virgo.virgoCryptoLib.Base58;
import io.virgo.virgoCryptoLib.Exceptions.Base58FormatException;
import io.virgo.virgoNode.Main;

public class Miscellaneous {

	public static String fileToString(String filename) {
	    String result = "";
	    try {
	        BufferedReader br = new BufferedReader(new FileReader(filename));
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();
	        while (line != null) {
	            sb.append(line);
	            line = br.readLine();
	        }
	        result = sb.toString();
	        br.close();
	    } catch(Exception e) {
	        e.printStackTrace();
	    }
	    return result;
	}
	
	public static boolean validateAddress(String hash, byte[] prefix) {
		try {
			byte[] decodedAddr = Base58.decodeChecked(hash);
			if(!byteArrayStartsWith(decodedAddr, 0, prefix))
				return false;
			return true;
		}catch(Base58FormatException e) {
			return false;
		}
	}
	
	public static boolean validateAmount(long amount) {
		if(amount <= 0)
			return false;
		
		return true;
	}
	
	public static boolean byteArrayStartsWith(byte[] source, int offset, byte[] match) {

		if(match.length > (source.length - offset))
			return false;

		for(int i = 0; i < match.length; i++)
	    	if(source[offset + i] != match[i])
	    		return false;
	    
		return true;
	}
	
	public static byte[] longToBytes(long x) {
	    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
	    buffer.putLong(x);
	    return buffer.array();
	}

}
