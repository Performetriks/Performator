package com.performetriks.performator.distribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.google.gson.JsonElement;
import com.xresch.xrutils.base.XR;

public class PFRAgentborneSettings {


	private boolean isCoordinator = false;
	
	private LinkedHashMap<String,String> envVariables = new LinkedHashMap<>();
	
	private ArrayList<String> jvmArgs = new ArrayList<>();
	
	
	private int agentAmount = 1; // amount of agents to use from the pool.
	private boolean useDataAgent = false; // define if a dataAgent should be selected from the pool
	
	private ArrayList<PFRAgent> agents = new ArrayList<>();
	private ArrayList<String> agentTags = new ArrayList<>();
	private ArrayList<String> dataAgentTags = new ArrayList<>();
	
	private DBReportSettings dbsettings = new DBReportSettings();

	/**************************************************************************************
	 * Constructor
	 **************************************************************************************/
	public PFRAgentborneSettings() {
		
	}
	
	public boolean isCoordinator() {
		return isCoordinator;
	}

	public PFRAgentborneSettings setCoordinator(boolean isCoordinator) {
		this.isCoordinator = isCoordinator;
		return this;
	}

	public LinkedHashMap<String, String> getEnvVariables() {
		return envVariables;
	}

	public PFRAgentborneSettings setEnvVariables(LinkedHashMap<String, String> envVariables) {
		this.envVariables = envVariables;
		return this;
	}

	public ArrayList<String> getJvmArgs() {
		return jvmArgs;
	}

	public PFRAgentborneSettings setJvmArgs(ArrayList<String> jvmArgs) {
		this.jvmArgs = jvmArgs;
		return this;
	}
	
	public int getAgentAmount() {
		return agentAmount;
	}
	
	public PFRAgentborneSettings setAgentAmount(int agentAmount) {
		this.agentAmount = agentAmount;
		return this;
	}
	
	public boolean getUseDataAgent() {
		return useDataAgent;
	}
	
	public PFRAgentborneSettings setUseDataAgent(boolean useDataAgent) {
		this.useDataAgent = useDataAgent;
		return this;
	}
	
	public ArrayList<PFRAgent> getAgents() {
		return agents;
	}

	public PFRAgentborneSettings setAgents(ArrayList<PFRAgent> agents) {
		this.agents = agents;
		return this;
	}

	public ArrayList<String> getAgentTags() {
		return agentTags;
	}

	public PFRAgentborneSettings setAgentTags(ArrayList<String> agentTags) {
		this.agentTags = agentTags;
		return this;
	}
	
	public ArrayList<String> getDataAgentTags() {
		return dataAgentTags;
	}

	public PFRAgentborneSettings setDataAgentTags(ArrayList<String> dataAgentTags) {
		this.dataAgentTags = dataAgentTags;
		return this;
	}

	public DBReportSettings getDbsettings() {
		return dbsettings;
	}

	public PFRAgentborneSettings setDbsettings(DBReportSettings dbsettings) {
		this.dbsettings = dbsettings;
		return this;
	}
	
	public PFRAgentborneSettings setDbsettings(
			  String host    
			, int    port
			, String dbName
			, String tableNamePrefix
			, String username
			, String password
			){ 
		
		this.dbsettings = new DBReportSettings(
				  host    
				, port
				, dbName
				, tableNamePrefix
				, username
				, password
			);
		return this;
	}
	
	public String toJsonString() {
		return XR.JSON.getGsonInstance().toJson(this);
	}
	
	public JsonElement toJson() {
		return XR.JSON.getGsonInstance().toJsonTree(this);
	}
	
	public static PFRAgentborneSettings fromJson(String json) {
		return XR.JSON.getGsonInstance().fromJson(json, PFRAgentborneSettings.class);
	}

	/**************************************************************************************
	 * Reads the information of the tests marked with @XRDiscoverable and other information.
	 **************************************************************************************/
	public class DBReportSettings {
		
		private String host;
		private int port = -1;
		private String dbName;
		private String tableNamePrefix;
		private String username;
		private String password;
		
		public DBReportSettings() { }
		
		public DBReportSettings(
				  String host    
				, int    port
				, String dbName
				, String tableNamePrefix
				, String username
				, String password
				){ 
			
			this.host             = host;
			this.port             = port;
			this.dbName            = dbName;
			this.tableNamePrefix  = tableNamePrefix;
			this.username         = username;
			this.password         = password;
		}
		
		
		public boolean isDefined() {
			if(this.host != null
			&& this.port != -1) {
				return true;
			}
			
			return false;
		}
		
		public String getHost() {
			return host;
		}
		public DBReportSettings setHost(String host) {
			this.host = host;
			return this;
		}
		public int getPort() {
			return port;
		}
		public DBReportSettings setPort(int port) {
			this.port = port;
			return this;
		}
		public String getDbName() {
			return dbName;
		}
		public DBReportSettings setDbName(String dbName) {
			this.dbName = dbName;
			return this;
		}
		public String getTableNamePrefix() {
			return tableNamePrefix;
		}
		public DBReportSettings setTableNamePrefix(String tableNamePrefix) {
			this.tableNamePrefix = tableNamePrefix;
			return this;
		}
		public String getUsername() {
			return username;
		}
		public DBReportSettings setUsername(String username) {
			this.username = username;
			return this;
		}
		public String getPassword() {
			return password;
		}
		public DBReportSettings setPassword(String password) {
			this.password = password;
			return this;
		}
		
		
	}
	
	
	
}
