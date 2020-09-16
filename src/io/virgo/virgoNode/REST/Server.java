package io.virgo.virgoNode.REST;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;

import com.sun.net.httpserver.HttpServer;

import io.virgo.virgoNode.Main;

//TODO: Complete API, make things configurable (require auth, enable or not API, choose serverPort..) 
public class Server {

	@SuppressWarnings("restriction")
	public Server() throws IOException {
		
		int serverPort = 8000;
		
        HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
        server.createContext("/", (exchange -> {
        	
        	String[] rawArgs = exchange.getRequestURI().toString().substring(1).split("/");
        	
        	if(rawArgs.length > 0) {
        		
            	String requestedServlet = rawArgs[0];
            	String[] requestArguments = Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
        		
            	Response response = new Response(405, "");// 405 Method Not Allowed
            	
            	System.out.println(requestedServlet);
        		
            	switch(requestedServlet) {
        			
        			case "addrtxs":
        				response = AddrTxsServlet.GET(requestArguments);
        				break;
        				
	        		case "tx":
	        			response = TxServlet.GET(requestArguments);
	        			break;
	        			
	        		case "nodeinfos":
	        			response = NodeInfosServlet.GET();
	        			break;
        		
        		}
        		
                exchange.sendResponseHeaders(response.getResponseCode(), response.getResponseBodyBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(response.getResponseBodyBytes());
                output.flush();
                exchange.close();
        		
        	} else {
        		exchange.sendResponseHeaders(405, -1);// 405 Method Not Allowed
        	}
        	
        }));
        server.setExecutor(null); // creates a default executor
        server.start();
	}
	
}
