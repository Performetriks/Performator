package com.performetriks.performator.distribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;

import ch.qos.logback.classic.Level;

/**********************************************************************************
 * 
 **********************************************************************************/
public class RemoteResponse {
	private static final Logger logger = LoggerFactory.getLogger(RemoteResponse.class);
	
	public static final String FIELD_PAYLOAD = "payload";
	public static final String FIELD_MESSAGES = "messages";
	public static final String FIELD_SUCCESS = "success";
	
	public static final String FIELD_STATUS_MEMORYTOTAL = "agentMemoryTotalMB";
	public static final String FIELD_STATUS_MEMORYFREE = "agentMemoryFreeMB";
	public static final String FIELD_STATUS_JAVAVERSION = "javaversion";
	public static final String FIELD_STATUS_PORT = "port";
	public static final String FIELD_STATUS_HOST = "host";
	public static final String FIELD_STATUS_AVAILABLE = "available";
	public static final String FIELD_STATUS_ISTESTRUNNING = "isTestRunning";
	
	JsonObject response;
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteResponse() {
		response = createResponseObject(true, null, null);
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteResponse(String json) {
		try {
			if(json != null && !json.isBlank()) {
				response = PFR.JSON.getGsonInstance()
						.fromJson(json, JsonObject.class);
			}else {
				response = RemoteResponse.createErrorObject(
						new Exception("Empty response received."));
			}
		} catch (Exception e) {
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
	public JsonObject payloadAsObject() {
		return response.get(FIELD_PAYLOAD).getAsJsonObject();
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public JsonArray payloadAsArray() {
		return response.get(FIELD_PAYLOAD).getAsJsonArray();
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public boolean payloadMemberAsBoolean(String memberName) {
		return response.get(FIELD_PAYLOAD)
					.getAsJsonObject()
					.get(memberName)
					.getAsBoolean();
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	private void handleMessages() {
		
		for(JsonElement message : this.messages()) {
			logger.error("Message from remote machine: "+PFR.JSON.toJSON(message));
		}

	}
	
	/********************************************************
	 * 
	 ********************************************************/
		public static JsonObject createErrorObject(Throwable e) {
		
		String message = e.getMessage() + "\r\n"+ PFR.Text.stacktraceToString(e);
		
		JsonArray messages = new JsonArray();
		messages.add(message);
		
		return createResponseObject(false, messages, null);
	}
		
	/********************************************************
	 * 
	 ********************************************************/
	public void setSuccess(boolean success) {
		response.addProperty(FIELD_SUCCESS, success);
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public void setPayload(JsonElement element) {
		response.add(FIELD_PAYLOAD, element);
	}
	
	/********************************************************
	 * Override all messages
	 ********************************************************/
	public void setMessages(JsonArray messages) {
		response.add(FIELD_MESSAGES, messages);
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void addMessage( Level level, String message) {
		
		if(response != null) {
			JsonArray messages = response.get(RemoteResponse.FIELD_MESSAGES).getAsJsonArray();
			
			JsonObject messageObject = new JsonObject();
			messageObject.addProperty("level", level.toString());
			messageObject.addProperty("message", message);
			
			messages.add(messageObject);
			
			logger.info("Add Response Message: " + PFR.JSON.toJSON(messageObject) );
		}
	}
	
	/********************************************************
	 * Override the data in the given response object with
	 * what this RemoteResponse instance contains.
	 ********************************************************/
	public JsonObject getResponse() {
		return response;
	}
	
	/********************************************************
	 * Override the data in the given response object with
	 * what this RemoteResponse instance contains.
	 ********************************************************/
	public String toJsonString() {
		return PFR.JSON.toJSON(response);
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	public static JsonObject createResponseObject(boolean success, JsonArray messages, JsonElement payload) {
		
		if(messages == null) { messages = new JsonArray(); }
		if(payload == null) { payload = new JsonObject(); }
		
		JsonObject responseObject = new JsonObject();
		responseObject.addProperty(FIELD_SUCCESS, success);
		responseObject.add(FIELD_MESSAGES, messages);
		responseObject.add(FIELD_PAYLOAD, payload);
		return responseObject;
	}
	
	/********************************************************
	 * Override the data in the given response object with
	 * what this RemoteResponse instance contains.
	 ********************************************************/
	public void overrideResponse(RemoteResponse otherResponse) {
		
		otherResponse.setSuccess(this.success());
		otherResponse.setMessages(this.messages());
		otherResponse.setPayload(this.payload());

	}
	
}