package io.virgo.virgoNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.geoWeb.GeoWeb;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.virgoNode.DAG.DAG;
import io.virgo.virgoNode.Data.Database;
import io.virgo.virgoNode.REST.Server;
import io.virgo.virgoNode.Utils.Miscellaneous;
import io.virgo.virgoNode.network.NetMessageHandler;
import io.virgo.geoWeb.exceptions.PortUnavailableException;


//TODO: Refactor log system
public class Main {
	
	public static final String VERSION = "0.0.5";
	
	public static long netId = 2946073207412533257l;//Default netId, testnet one
	public static int peerCountTarget = 8;
	public static int netPort = 25565;
	
	private static GeoWeb net;
	private static DAG dag;
	private static Database db;
	
	public static String dbFileName = "database.db";
	public static int tipsSaveInterval = 10000;
	
	public static final int DECIMALS = 8;
	public static final long BEACON_REWARD = (long) (5 * Math.pow(10, DECIMALS));
	public static final byte[] ADDR_IDENTIFIER = new BigInteger("4039").toByteArray();
	public static final BigInteger MAX_DIFFICULTY = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");//value of 0x00000000FFFF0000000000000000000000000000000000000000000000000000
	
	//ugly debug stats
	public static int txsSec = 0;
	static String[] runningIndicators = {"-", "\\", "|", "/"};
	static int currentIndicator = 0;
	
	/**
	 * Main function called on Application startup
	 */
	public static void main(String[] none) {
		
		System.out.println(getAppName()+"\n"
				+ "Visit https://virgo.net for more informations !");
		
		loadConfig();
		
		System.out.println("Loaded config");
		
		//init SQLite database, storing received transactions
		try {
			db = new Database(dbFileName);
		}catch(SQLException e) {
			System.out.println("Error loading database: " + e.getMessage());
			System.out.println("Terminating.");
		}

		//init GeoWeb (peer to peer networking) with a NetMessageHandler instance as messageHandler and loaded parameters 
		//TODO: make Thread pool size configurable
		try {
			
			GeoWeb.Builder builder = new GeoWeb.Builder();
			net = builder.port(netPort)
					.netID(netId)
					.peerCountTarget(peerCountTarget)
					.messageHandler(new NetMessageHandler())
					.maxMessageThreadPoolSize(10)
					.build();
			
		} catch (IllegalArgumentException | IOException e) {
			System.out.println(e.getMessage());
			System.out.println("Unexpected error during initialisation, terminating.");
			return;
		} catch (PortUnavailableException e) {
			System.out.println("Given port (" + netPort + ") unavailable, terminating.");
			return;
		}
		
		System.out.println("P2P adapter loaded ! Listening on port " + netPort);
		
		System.out.println("Creating DAG");
		
		dag = new DAG(tipsSaveInterval);
		new Thread(dag).start();
		
		System.out.println("Running REST server on port 8000");
		try {
			new Server();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//ugly debug stats
		new Timer().scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				
				double usedMB = (double) used / (double)(1024*1024);
				double maxMB = (double) Runtime.getRuntime().maxMemory() / (double)(1024*1024);
				
				NumberFormat formatter = new DecimalFormat("#0.00");
				
				System.out.print("\r " + txsSec + " txs/s | " + dag.loadedTxsCount() + " in total | " + dag.getPoolSize() + " txs waiting | " + net.getPeers().size() + " peers | " +
					formatter.format(usedMB) + "/" + formatter.format(maxMB) + "MB " + runningIndicators[currentIndicator]);
				txsSec = 0;
				currentIndicator++;
				if(currentIndicator > 3)
					currentIndicator = 0;
			}
			
		}, 1000l, 1000l);
				
	}
	
	
	/**
	 * searches for config file and load it, if not found create one using default values
	 */
	private static void loadConfig() {
		
		File configFile = new File("config.json");
		
		if(configFile.exists()) {
			  
			String configString = Miscellaneous.fileToString("config.json");
			
			try {
				JSONObject config = new JSONObject(configString);
				
				netId = config.has("netId") ? config.getLong("netId") : netId;
				peerCountTarget = config.has("peerCountTarget") ? config.getInt("peerCountTarget") : peerCountTarget;
				netPort = config.has("port") ? (AddressUtils.isValidPort(config.getInt("port")) ? config.getInt("port") : netPort) : netPort;
				dbFileName = config.has("dbFileName") ? config.getString("dbFileName") : dbFileName;
				tipsSaveInterval = config.has("tipsSaveInterval") ? config.getInt("tipsSaveInterval") : tipsSaveInterval;
				
				System.out.println("config.json successfully loaded !");
			}catch(JSONException e) {
				System.out.println("Error in config.json, using default values");
			}
			
			return;
		}
		
		System.out.println("config.json don't exist, creating one with default values");
		
		try {
			configFile.createNewFile();
			
			JSONObject defaultConfig = new JSONObject();
			defaultConfig.put("netId", netId);
			defaultConfig.put("peerCountTarget", peerCountTarget);
			defaultConfig.put("port", netPort);
			defaultConfig.put("dbFileName", dbFileName);
			defaultConfig.put("tipsSaveInterval", tipsSaveInterval);
			
			FileWriter writer = new FileWriter(configFile);
			writer.write(defaultConfig.toString(4));
			writer.close();
			
		} catch (IOException e) {
			System.out.println("Unable to write config.json, please check permissions and disk usage\n"
					+ "Launching with default values");
			e.printStackTrace();
		}
		
		
	}

	public static DAG getDAG() {
		return dag;
	}
	
	public static GeoWeb getGeoWeb() {
		return net;
	}
	
	public static Database getDatabase() {
		return db;
	}
	
	public static String getAppName() {
		return "VirgoNode Java version "+VERSION;
	}

	public static long getNetID() {
		return netId;
	}

}
