package io.virgo.virgoNode.REST;


//REST API get response data representation
public class Response {
	
	private int responseCode;
	private String responseBody;
	
	public Response(int responseCode, String responseBody) {
		this.responseCode = responseCode;
		this.responseBody = responseBody;
	}

	public int getResponseCode() {
		return responseCode;
	}
	
	public String getResponseBody() {
		return responseBody;
	}
	
	public byte[] getResponseBodyBytes() {
		return getResponseBody().getBytes();
	}
	
}
