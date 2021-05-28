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
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.Transaction;
import io.virgo.virgoNode.DAG.TxOutput;
import io.virgo.virgoNode.DAG.TxVerificationPool.jsonVerificationTask;

public class Database {

	private Connection conn;
	
	public Database(String dbname) throws SQLException {
		
		conn = DriverManager.getConnection("jdbc:sqlite:"+dbname);
		
		createTables();
		
	}
	
	private void createTables() throws SQLException {
		
		Statement tipsCreateStmt = conn.createStatement();
		tipsCreateStmt.execute("CREATE TABLE IF NOT EXISTS tips (id text PRIMARY key, height integer);");
		
		Statement txsCreateStmt = conn.createStatement();
		txsCreateStmt.execute("CREATE TABLE IF NOT EXISTS txs (id text PRIMARY key, sig data, pubKey data, parents text, inputs text, outputs text, parentBeacon data, nonce data, date integer);");
		
	}
	
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
	
	public void setTips(LoadedTransaction[] tips) throws SQLException {
		
		JSONArray oldTips = getTips();
		
		long highestHeight = 0;
		
		for(int i = 0; i < oldTips.length(); i++) {
			
			long tipHeight = oldTips.getJSONObject(i).getLong("height");
			
			if(tipHeight > highestHeight)
				highestHeight = tipHeight;
			
		}
		
		ArrayList<LoadedTransaction> tipsToAdd = new ArrayList<LoadedTransaction>();
		
		for(LoadedTransaction tip : tips) {
			
			if(tip.getHeight() >= highestHeight)
				tipsToAdd.add(tip);
			
		}
		
		if(tipsToAdd.size() > 0) {
			
			Statement deleteStmt = conn.createStatement();
			deleteStmt.executeUpdate("DELETE FROM tips");
			
			for(LoadedTransaction tip : tips) {
				PreparedStatement insertTips = conn.prepareStatement("INSERT OR IGNORE INTO tips(id,height) VALUES(?,?)");
				insertTips.setString(1, tip.getHash().toString());
				insertTips.setLong(2, tip.getHeight());
				insertTips.execute();
			}
			
		}
		
	}
	
	public JSONArray getTips() throws SQLException {
		
		Statement getTipsStmt = conn.createStatement();
		
		JSONArray tipsArray = new JSONArray();
		
		if(getTipsStmt.execute("SELECT * FROM tips")) {
			ResultSet rs = getTipsStmt.getResultSet();
			
			while(rs.next()) {
				JSONObject tip = new JSONObject();
				tip.put("id",rs.getString(1));
				tip.put("height", rs.getLong(2));
				tipsArray.put(tip);
			}
			
		}
		
		return tipsArray;
		
	}
	
	/**
	 * Load all stored transactions to the DAG by insert order
	 */
	public void loadAllTransactions(DAG dag) throws SQLException {
		
		Statement getTransactionsStmt = conn.createStatement();
		
		if(getTransactionsStmt.execute("SELECT * FROM txs")) {
			
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
