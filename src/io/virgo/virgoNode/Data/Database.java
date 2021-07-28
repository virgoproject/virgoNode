package io.virgo.virgoNode.Data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.Transaction;
import io.virgo.virgoNode.DAG.TxOutput;

/**
 * SQLite database helper class
 * Stores received transactions on local disk
 */
public class Database {

	private Connection conn;
	
	
	public Database(String dbname) throws SQLException {
		
		conn = DriverManager.getConnection("jdbc:sqlite:"+dbname);
		
		createTables();
		
	}
	
	/**
	 * Create tables on database if not exist
	 */
	private void createTables() throws SQLException {
		
		Statement txsCreateStmt = conn.createStatement();
		txsCreateStmt.execute("CREATE TABLE IF NOT EXISTS txs (id text PRIMARY key, sig data, pubKey data, parents text, inputs text, outputs text, parentBeacon data, nonce data, date integer);");
		
	}
	
	/**
	 * Insert a transaction into database
	 */
	public void insertTx(Transaction tx) throws SQLException {
		
		PreparedStatement insertStmt = conn.prepareStatement("INSERT OR IGNORE INTO txs (id, sig, pubKey, parents, inputs, outputs, parentBeacon, nonce, date) VALUES (?,?,?,?,?,?,?,?,?)");
    	
    	insertStmt.setString(1, tx.getHash().toString());
    	insertStmt.setString(4,  new JSONArray(tx.getParentsHashesStrings()).toString());
    	
    	if(tx.getParentBeaconHash() == null) {
        	insertStmt.setBytes(2, tx.getSignature().toByteArray());
        	insertStmt.setBytes(3, tx.getPublicKey());
    		insertStmt.setString(5,  new JSONArray(tx.getInputsHashesStrings()).toString());
    		insertStmt.setBytes(7, null);
    		insertStmt.setBytes(8, null);
    	} else {
    		insertStmt.setBytes(2, null);
    		insertStmt.setBytes(3, null);
    		insertStmt.setString(5, null);
    		insertStmt.setBytes(7, tx.getParentBeaconHash().toBytes());
      		insertStmt.setBytes(8, tx.getNonce());
    	}
    		
    		
		JSONArray outputsJson = new JSONArray();
		for(Map.Entry<String, TxOutput> entry : tx.getOutputsMap().entrySet())
		   outputsJson.put(entry.getValue().toString());
		insertStmt.setString(6, outputsJson.toString());
		insertStmt.setLong(9, tx.getDate());
		
    	insertStmt.executeUpdate();
		
	}
	
	/**
	 * Retrieve a transaction from database
	 * 
	 * @return a JSONObject representing the transaction corresponding to the given hash, or null if not found
	 */
	public JSONObject getTx(Sha256Hash txId) throws SQLException {
		
        String sql = "SELECT * FROM txs WHERE id='"+txId.toString()+"'";
        
        Statement stmt = conn.createStatement();
        ResultSet result = stmt.executeQuery(sql);
		
        if(result.next()) {
        	JSONObject txJson = new JSONObject();
        	
    		txJson.put("parents", new JSONArray(result.getString("parents")));
    		
    		byte[] parentBeaconBytes = result.getBytes("parentBeacon");
    		
    		if(parentBeaconBytes == null) {
        		txJson.put("sig", Converter.bytesToHex(result.getBytes("sig")));
        		txJson.put("pubKey", Converter.bytesToHex(result.getBytes("pubKey")));
    			txJson.put("inputs", new JSONArray(result.getString("inputs")));
    		} else {
    			txJson.put("parentBeacon", new Sha256Hash(parentBeaconBytes).toString());
    			txJson.put("nonce", Converter.bytesToHex(result.getBytes("nonce")));
    		}
    			
    		txJson.put("outputs", new JSONArray(result.getString("outputs")));
    		txJson.put("date", result.getLong("date"));
        	
    		return txJson;
    		
        }
        
        return null;
	}
	
	public ArrayList<Sha256Hash> getInsertedAfter(Sha256Hash txHash) throws SQLException {
		
		ArrayList<Sha256Hash> txsHashes = new ArrayList<Sha256Hash>();
		
		long date = -1l;
		
		if(!txHash.equals(Main.getDAG().getGenesis().getHash())) {
			PreparedStatement stmt = conn.prepareStatement("SELECT date FROM txs WHERE id=?");
			stmt.setString(1, txHash.toString());
			
			ResultSet res = stmt.executeQuery();
			
			if(res.next())
				date = res.getLong("date");
			
		}else {
			date = 0l;
		}
		
		if(date != -1l) {
			PreparedStatement stmt2 = conn.prepareStatement("SELECT id FROM txs WHERE date>? ORDER BY date ASC LIMIT 500");
			stmt2.setLong(1, date);
			
			ResultSet result = stmt2.executeQuery();
			
			while(result.next())
				txsHashes.add(new Sha256Hash(result.getString("id")));
		}
		
		return txsHashes; 
		
	}
	
	public ArrayList<Sha256Hash> getInsertedBefore(Sha256Hash txHash, Sha256Hash maxAncestorHash) throws SQLException {
		
		ArrayList<Sha256Hash> txsHashes = new ArrayList<Sha256Hash>();

		if(!txHash.equals(Main.getDAG().getGenesis().getHash()) && !maxAncestorHash.equals(Main.getDAG().getGenesis().getHash())) {
		
			long max = 0l;
			long min = 0l;
			
			PreparedStatement stmt = conn.prepareStatement("SELECT date FROM txs WHERE id=?");
			stmt.setString(1, txHash.toString());
			
			ResultSet res = stmt.executeQuery();
			
			if(res.next())
				max = res.getLong("date");
			else return txsHashes;
			
			
			stmt = conn.prepareStatement("SELECT date FROM txs WHERE id=?");
			stmt.setString(1, maxAncestorHash.toString());
			
			res = stmt.executeQuery();
			
			if(res.next())
				min = res.getLong("date");
			
			if(min > max)
				min = 0l;
			
			stmt = conn.prepareStatement("SELECT id FROM txs WHERE date<? AND date>? ORDER BY date ASC LIMIT 500");
			stmt.setLong(1, max);
			stmt.setLong(2, min);
			
			res = stmt.executeQuery();
			
			while(res.next())
				txsHashes.add(new Sha256Hash(res.getString("id")));
			
		}
		
		return txsHashes;
		
	}
	
	/**
	 * Load all stored transactions to the DAG by insert order
	 */
	public void loadAllTransactions(DAG dag) throws SQLException {
		
		Statement getTransactionsStmt = conn.createStatement();
		
		if(getTransactionsStmt.execute("SELECT * FROM txs ORDER BY date ASC")) {
			
			ResultSet result = getTransactionsStmt.getResultSet();
			
			while(result.next()) {
				
	        	JSONObject txJson = new JSONObject();
	        	
	    		txJson.put("parents", new JSONArray(result.getString("parents")));
	    		
	    		byte[] parentBeaconBytes = result.getBytes("parentBeacon");
	    		
	    		if(parentBeaconBytes == null) {
	        		txJson.put("sig", Converter.bytesToHex(result.getBytes("sig")));
	        		txJson.put("pubKey", Converter.bytesToHex(result.getBytes("pubKey")));
	    			txJson.put("inputs", new JSONArray(result.getString("inputs")));
	    		} else {
	    			txJson.put("parentBeacon", new Sha256Hash(parentBeaconBytes).toString());
	    			txJson.put("nonce", Converter.bytesToHex(result.getBytes("nonce")));
	    		}
	    			
	    		txJson.put("outputs", new JSONArray(result.getString("outputs")));
	    		txJson.put("date", result.getLong("date"));
	        	
				dag.verificationPool. new jsonVerificationTask(txJson, true);
				
			}
			
		}
	}
	
}
