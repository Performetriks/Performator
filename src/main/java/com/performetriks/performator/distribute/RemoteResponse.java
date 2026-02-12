package com.performetriks.performator.distribute;

import java.io.BufferedReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;

/**********************************************************************************
 * 
 **********************************************************************************/
public class RemoteResponse {
	
	private static final Logger logger = LoggerFactory.getLogger(RemoteResponse.class);
	
	private static final String FIELD_PAYLOAD = "payload";
	private static final String FIELD_MESSAGES = "messages";
	private static final String FIELD_SUCCESS = "success";
	
	JsonObject response;
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteResponse(BufferedReader clientReader) {
		StringBuilder jsonBuilder = new StringBuilder();
		String line;
		try {
			while ((line = clientReader.readLine()) != null) {
				//if ("END".equals(line)) break;
				jsonBuilder.append(line);
				
			}
			if(jsonBuilder.length() > 0) {
				response = PFR.JSON.getGsonInstance().fromJson(jsonBuilder.toString(), JsonObject.class);
			}else {
				response = RemoteResponse.createErrorObject(new Exception("Empty response received."));
			}
			
		} catch (IOException e) {

			response = RemoteResponse.createErrorObject(e);
		} finally {
			handleMessages();
		}
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public boolean success() {
		return response.get(FIELD_SUCCESS).getAsBoolean();
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public JsonArray messages() {
		return response.get(FIELD_MESSAGES).getAsJsonArray();
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public JsonElement payload() {
		return response.get(FIELD_PAYLOAD);
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	private void handleMessages() {
		
		for(JsonElement message : this.messages()) {
			logger.error(message.getAsString());
		}

	}
	
	/********************************************************
	 * 
	 ********************************************************/
		public static JsonObject createErrorObject(Throwable e) {
		
		String message = e.getMessage() + "\r\n"+ e.fillInStackTrace();
		
		JsonArray messages = new JsonArray();
		messages.add(message);
		
		return createResponseObject(false, messages, null);
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public static JsonObject createResponseObject(boolean success, JsonArray messages, JsonElement payload) {
		
		if(messages == null) { messages = new JsonArray(); }
		
		JsonObject responseObject = new JsonObject();
		responseObject.addProperty(FIELD_SUCCESS, success);
		responseObject.add(FIELD_MESSAGES, messages);
		responseObject.add(FIELD_PAYLOAD, payload);
		return responseObject;
	}
	
}