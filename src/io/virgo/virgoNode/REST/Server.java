package io.virgo.virgoNode.REST;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Executors;

import javax.naming.ConfigurationException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpExchange;

import io.virgo.virgoNode.Utils.Miscellaneous;

import com.sun.net.httpserver.HttpsParameters;

//TODO: Complete API, make things configurable (require auth, enable or not API, choose serverPort..)
/**
 * REST API http server
 */
public class Server {

	public Server() throws IOException {
		
		int serverPort = 8000;
        
		try {
	        SSLContext sslContext = SSLContext.getInstance("TLS");
			
			KeyStore ks = getKeyStore();
			
	        // Set up the key manager factory
	        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
	        kmf.init(ks, "whatever".toCharArray());

	        // Set up the trust manager factory
	        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
	        tmf.init(ks);

	        // Set up the HTTPS context and parameters
	        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			
	        HttpsServer server = HttpsServer.create(new InetSocketAddress(serverPort), 0);
	        server.createContext("/", (exchange -> {
	        	handle(exchange);
	        }));
			
	        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
	     
	        server.setExecutor(Executors.newCachedThreadPool()); // creates a default executor
	        server.start();
	        
	        System.out.println("Running REST server over HTTPS");
	        
		} catch (Exception e) {
			System.out.println("Missing or invalid SSL/TLS certificate, running REST server over HTTP");
			
	        HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
	        server.createContext("/", (exchange -> {
	        	handle(exchange);
	        }));
			
	        server.setExecutor(Executors.newCachedThreadPool()); // creates a default executor
	        server.start();
		}
        
	}
	
	public void handle(HttpExchange exchange) throws IOException {
    	String[] rawArgs = exchange.getRequestURI().toString().substring(1).split("/");
    	
    	if(rawArgs.length > 0) {
    		
        	String requestedServlet = rawArgs[0];
        	String[] requestArguments = Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
    		
        	String requestBody = new String(exchange.getRequestBody().readAllBytes());
        	
        	Response response = new Response(405, "");// 405 Method Not Allowed
        	
        	switch(requestedServlet) {
    			
    			case "address":
    				response = AddressServlet.GET(requestArguments);
    				break;
    				
        		case "tx":
        			if(requestBody.equals(""))
        				response = TxServlet.GET(requestArguments);
        			else
        				response = TxServlet.POST(requestArguments, requestBody);
        			break;
        			
        		case "nodeinfos":
        			response = NodeInfosServlet.GET();
        			break;
        			
        		case "tips":
        			response = TipsServlet.GET();
        			break;
        		
        		case "work":
        			response = WorkServlet.GET();
        			break;
        			
        		case "beacon":
        			response = beaconServlet.GET(requestArguments);
        			break;
        			
    		}
        	exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(response.getResponseCode(), response.getResponseBodyBytes().length);
            
            OutputStream output = exchange.getResponseBody();
            output.write(response.getResponseBodyBytes());
            
            output.flush();
            output.close();
    		
    	} else {
    		exchange.sendResponseHeaders(405, -1);// 405 Method Not Allowed
    	}
    	
        exchange.close();
	}
	
	private KeyStore getKeyStore() throws ConfigurationException {
	    try {
	        Certificate clientCertificate = loadCertificate(new File("cert.pem"));
	        PrivateKey privateKey = loadPrivateKey(new File("cert.key"));

	        KeyStore keyStore = KeyStore.getInstance("JKS");
	        keyStore.load(null, null);
	        keyStore.setCertificateEntry("client-cert", clientCertificate);
	        keyStore.setKeyEntry("client-key", privateKey, "whatever".toCharArray(), new Certificate[]{clientCertificate});
	        return keyStore;
	    } catch (GeneralSecurityException | IOException e) {
	        throw new ConfigurationException("Cannot build keystore");
	    }
	}

	private Certificate loadCertificate(File certificatePem) throws IOException, GeneralSecurityException {
	    CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
	    final byte[] content = parsePEMFile(certificatePem);
	    return certificateFactory.generateCertificate(new ByteArrayInputStream(content));
	}


	private static byte[] parsePEMFile(File pemFile) throws IOException {
		  if (!pemFile.isFile() || !pemFile.exists()) {
		    throw new FileNotFoundException(String.format("The file '%s' doesn't exist.", pemFile.getAbsolutePath()));
		  }
		  PemReader reader = new PemReader(new FileReader(pemFile));
		  PemObject pemObject = reader.readPemObject();
		  byte[] content = pemObject.getContent();
		  reader.close();
		  return content;
		}

	private static PrivateKey loadPrivateKey(final File keyFile) throws IOException {
		  try (final PemReader pemReader = new PemReader(new FileReader(keyFile))) {
		    final PemObject pemObject = pemReader.readPemObject();
		    final byte[] content = pemObject.getContent();
		    final PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
		    final KeyFactory factory = KeyFactory.getInstance("RSA");
		    return factory.generatePrivate(privKeySpec);
		  } catch (NoSuchAlgorithmException e) {
		    throw new IOException("No encryption provider available.", e);
		  } catch (final InvalidKeySpecException e) {
		    throw new IOException("Invalid Key format.", e);
		  }
	}
	
}
