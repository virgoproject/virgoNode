package io.virgo.virgoNode.REST;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

//TODO: Complete API, make things configurable (require auth, enable or not API, choose serverPort..)
/**
 * REST API http server
 */
public class Server {

	public Server() throws IOException {
		
		int serverPort = 8000;
		
        HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
        server.createContext("/", (exchange -> {
        	
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
        	
        }));
        server.setExecutor(Executors.newCachedThreadPool()); // creates a default executor
        server.start();
	}
	
}
