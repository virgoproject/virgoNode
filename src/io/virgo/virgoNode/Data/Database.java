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
import io.virgo.virgoNode.Main;
import io.virgo.virgoNode.DAG.LoadedTransaction;
import io.virgo.virgoNode.DAG.Transaction;
import io.virgo.virgoNode.DAG.TxOutput;
import io.virgo.virgoNode.Utils.Miscellaneous;

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
		txsCreateStmt.execute("CREATE TABLE IF NOT EXISTS txs (id text PRIMARY key, sig data, pubKey data, parents text, inputs text, outputs text, parentBeacon text, nonce String, date integer);");
		
	}
	
	public void insertTx(Transaction tx) throws SQLException {
		
		PreparedStatement insertStmt = conn.prepareStatement("INSERT OR IGNORE INTO txs (id, sig, pubKey, parents, inputs, outputs, parentBeacon, nonce, date) VALUES (?,?,?,?,?,?,?,?,?)");
    	
    	insertStmt.setString(1, tx.getUid());
    	insertStmt.setString(4,  new JSONArray(tx.getParentsUids()).toString());
    	
    	
    	if(tx.getParentBeaconUid() == null) {
        	insertStmt.setBytes(2, tx.getSignature().toByteArray());
        	insertStmt.setBytes(3, tx.getPublicKey());
    		insertStmt.setString(5,  new JSONArray(tx.getInputsUids()).toString());
    	} else {
    		insertStmt.setBytes(2, null);
    		insertStmt.setBytes(3, null);
    		insertStmt.setString(5, null);
    	}
    		
    		
		JSONArray outputsJson = new JSONArray();
		for(Map.Entry<String, TxOutput> entry : tx.getOutputsMap().entrySet())
		   outputsJson.put(entry.getValue().toString());
		insertStmt.setString(6, outputsJson.toString());
    	
		insertStmt.setString(7, tx.getParentBeaconUid());
		insertStmt.setString(8, tx.getNonce());
		insertStmt.setLong(9, tx.getDate());
		
    	insertStmt.executeUpdate();
		
	}
	
	public JSONObject getTx(String txId) throws SQLException {
		if(!Miscellaneous.validateAddress(txId, Main.TX_IDENTIFIER))
			return null;
		
        String sql = "SELECT * FROM txs WHERE id='"+txId+"'";
        
        Statement stmt = conn.createStatement();
        ResultSet result = stmt.executeQuery(sql);
		
        if(result.next()) {
        	JSONObject txJson = new JSONObject();
        	
    		txJson.put("parents", new JSONArray(result.getString("parents")));
    		
    		String parentBeacon = result.getString("parentBeacon");
    		
    		if(parentBeacon == null) {
        		txJson.put("sig", Converter.bytesToHex(result.getBytes("sig")));
        		txJson.put("pubKey", Converter.bytesToHex(result.getBytes("pubKey")));
    			txJson.put("inputs", new JSONArray(result.getString("inputs")));
    		} else {
    			txJson.put("parentBeacon", parentBeacon);
    			txJson.put("nonce", result.getLong("nonce"));
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
				insertTips.setString(1, tip.getUid());
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
	
}
