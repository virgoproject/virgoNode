package io.virgo.virgoNode.Data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.LoadedTransaction;
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
		txsCreateStmt.execute("CREATE TABLE IF NOT EXISTS txs (id text PRIMARY key, sig data, pubKey data, parents text, inputs text, outputs text, parentBeacon data, nonce data, date integer, height integer);");
		
		Statement dropStatesStmt = conn.createStatement();
		dropStatesStmt.execute("DROP TABLE IF EXISTS states");
		
		Statement statesCreateStmt = conn.createStatement();
		statesCreateStmt.execute("CREATE TABLE states (id text PRIMARY key, state text);");
		
	}
	
	/**
	 * Insert a transaction into database
	 */
	public void insertTx(LoadedTransaction tx) throws SQLException {
		
		PreparedStatement insertStmt = conn.prepareStatement("INSERT OR IGNORE INTO txs (id, sig, pubKey, parents, inputs, outputs, parentBeacon, nonce, date, height) VALUES (?,?,?,?,?,?,?,?,?,?)");
    	
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
		insertStmt.setLong(10, tx.getHeight());
		
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
	
	public ArrayList<Sha256Hash> getInsertedAfter(Sha256Hash txHash, int wanted) throws SQLException {
		
		ArrayList<Sha256Hash> txsHashes = new ArrayList<Sha256Hash>();
		
		long height = -1l;
		
		if(!txHash.equals(Main.getDAG().getGenesis().getHash())) {
			PreparedStatement stmt = conn.prepareStatement("SELECT height FROM txs WHERE id=?");
			stmt.setString(1, txHash.toString());
			
			ResultSet res = stmt.executeQuery();
			
			if(res.next())
				height = res.getLong("height");
			
		}else {
			height = 0l;
		}
		
		if(height != -1l) {
			PreparedStatement stmt2 = conn.prepareStatement("SELECT id FROM txs WHERE height>=? ORDER BY height ASC LIMIT ?");
			stmt2.setLong(1, height);
			stmt2.setInt(2, wanted);
			
			ResultSet result = stmt2.executeQuery();
			
			while(result.next())
				txsHashes.add(new Sha256Hash(result.getString("id")));
		}
				
		return txsHashes; 
		
	}
	
	public ArrayList<Sha256Hash> getInsertedBefore(Sha256Hash txHash, Sha256Hash maxAncestorHash, int wanted) throws SQLException {
		
		ArrayList<Sha256Hash> txsHashes = new ArrayList<Sha256Hash>();

		if(!txHash.equals(Main.getDAG().getGenesis().getHash())) {
		
			long max = 0l;
			long min = 0l;
			
			PreparedStatement stmt = conn.prepareStatement("SELECT height FROM txs WHERE id=?");
			stmt.setString(1, txHash.toString());
			
			ResultSet res = stmt.executeQuery();
			
			if(res.next())
				max = res.getLong("height");
			else return txsHashes;
			
			if(!maxAncestorHash.equals(Main.getDAG().getGenesis().getHash())) {
				stmt = conn.prepareStatement("SELECT height FROM txs WHERE id=?");
				stmt.setString(1, maxAncestorHash.toString());
				
				res = stmt.executeQuery();
				
				if(res.next())
					min = res.getLong("height");
				
				if(min > max)
					min = 0l;
			}
			
			stmt = conn.prepareStatement("SELECT id FROM txs WHERE height<=? AND height>=? ORDER BY height DESC LIMIT ?");
			stmt.setLong(1, max);
			stmt.setLong(2, min);
			stmt.setInt(3, wanted);
						
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
		
		if(getTransactionsStmt.execute("SELECT * FROM txs ORDER BY height ASC")) {
			
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
	        	
				dag.verificationPool. new jsonVerificationTask(txJson, true, false);
				
			}
			
		}
	}

	public void insertState(LoadedTransaction tx) throws SQLException {
		PreparedStatement insertStmt = conn.prepareStatement("REPLACE INTO states (id, state) VALUES (?,?)");
    	
    	insertStmt.setString(1, tx.getHash().toString());
    	insertStmt.setString(2, tx.JSONState().toString());
    	
    	insertStmt.executeUpdate();
	}

	public boolean insertStates(ArrayList<LoadedTransaction> txsToPrune) throws SQLException {
		
		conn.setAutoCommit(false);
		
		for(LoadedTransaction tx : txsToPrune) {
			try {
				PreparedStatement insertStmt = conn.prepareStatement("REPLACE INTO states (id, state) VALUES (?,?)");
		    	insertStmt.setString(1, tx.getHash().toString());
		    	insertStmt.setString(2, tx.JSONState().toString());
		    	
		    	insertStmt.executeUpdate();
			}catch(SQLException e) {
				conn.rollback();
				return false;
			}

		}
		
		conn.commit();
		return true;
	}
	
	public JSONObject getState(Sha256Hash hash) throws SQLException {
		
        String sql = "SELECT * FROM states WHERE id='"+hash.toString()+"'";
        
        Statement stmt = conn.createStatement();
        ResultSet result = stmt.executeQuery(sql);
		
        if(result.next())
        	return new JSONObject(result.getString("state"));
        
		return null;
		
	}
	
}
