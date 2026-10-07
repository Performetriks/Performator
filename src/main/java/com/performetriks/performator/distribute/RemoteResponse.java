package com.performetriks.performator.distribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.performetriks.performator.base.PFR;
import com.xresch.xrutils.data.XRValue;

import ch.qos.logback.classic.Level;



/**********************************************************************************
 * 
 **********************************************************************************/
public class RemoteResponse {

	private static final Logger logger = LoggerFactory.getLogger(RemoteResponse.class);
	
	public static final String FIELD_PAYLOAD = "payload";
	public static final String FIELD_MESSAGES = "messages";
	public static final String FIELD_SUCCESS = "success";
	
	private static final String MESSAGEFIELD_MSG = "message";
	private static final String MESSAGEFIELD_LEVEL = "level";
	
	JsonObject response;
	
	private ZePFRClient client;
	
	public enum AgentStatusFields{
		  // These names must be the same as the record below
		  available
		, isCoordinator
		, isDataAgent
		, isTestRunning
		, starttime
		, maxDuration
		, execid
		, host
		, port
		, javaversion
		, agentMemoryTotalMB
		, agentMemoryFreeMB
	};
			
	public record AgentStatus(
			  String available
			, Boolean isCoordinator
			, Boolean isDataAgent
			, Boolean isTestRunning
			, Long starttime
			, Long maxDuration
			, String execid
			, String host
			, Integer port
			, String javaVersion
			, Long agentMemoryTotalMB
			, Long agentMemoryFreeMB
			){
		
	};
	
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
	public RemoteResponse(ZePFRClient client, String json) {
		this(json);
		this.client = client;
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
	public boolean payloadAsBoolean() {
		
		XRValue value = XRValue.newFromJsonElement(response.get(FIELD_PAYLOAD));
		
		return value.getAsBoolean();

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
		
		for(JsonElement element : this.messages()) {
			JsonObject object = element.getAsJsonObject();
			
			String level = object.get(MESSAGEFIELD_LEVEL).getAsString();
			String message = object.get(MESSAGEFIELD_MSG).getAsString();
			
			String messagePrefix = "Message from remote machine: ";
			if(client != null) {
				messagePrefix = "Message from ["+client.getHostAndPort()+"]: ";
			}
			
			switch(level.toUpperCase()) {
				case "ERROR":	logger.error(messagePrefix + message); break;
				case "WARN":	logger.warn(messagePrefix + message); break;
				case "INFO":	logger.info(messagePrefix + message); break;
				case "DEBUG":	logger.debug(messagePrefix + message); break;
				case "TRACE":	logger.trace(messagePrefix + message); break;
				default:		logger.info(messagePrefix + " [" + level +"] " + message); break;

			}
			
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
	 * 
	 ********************************************************/
	public void setPayload(boolean bool) {
		response.add(FIELD_PAYLOAD, new JsonPrimitive(bool));
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
	public void addMessage(Level level, String message) {
		
		if(response != null) {
			JsonArray messages = response.get(RemoteResponse.FIELD_MESSAGES).getAsJsonArray();
			
			JsonObject messageObject = new JsonObject();
			messageObject.addProperty(MESSAGEFIELD_LEVEL, level.toString());
			messageObject.addProperty(MESSAGEFIELD_MSG, message);
			
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
		
		if(otherResponse == null) { return;}
		
		otherResponse.setSuccess(this.success());
		otherResponse.setMessages(this.messages());
		otherResponse.setPayload(this.payload());

	}
	
}