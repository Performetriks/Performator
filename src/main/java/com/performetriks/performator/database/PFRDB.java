package com.performetriks.performator.database;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord;

/**************************************************************************************************************
 * Performator Interface to test database queries.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 **************************************************************************************************************/
public class PFRDB {

	private static Logger logger = LoggerFactory.getLogger(PFRDB.class.getName());
	
	protected ThreadLocal<ArrayList<Connection>> myOpenConnections = new ThreadLocal<>();
	protected ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

	private BasicDataSource pooledSource;
	

	private static HashMap<String, BasicDataSource> managedConnectionPools = new HashMap<>();
	
	public PFRDB(BasicDataSource pooledSource) {
		this.pooledSource = pooledSource;
	}
	
	/********************************************************************************************
	 * Get the Datasource for this DBInterface.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public DataSource getDatasource() {
		return pooledSource;
	}
	
	/********************************************************************************************
	 * Get a connection from the connection pool or returns the current connection used for the 
	 * transaction.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public Connection getConnection() throws SQLException {
		
		//Improve performance, reduce memory overhead
		if(logger.isTraceEnabled()) {
			logger
				.trace("DB Connections Active: "+pooledSource.getNumActive());
		}
		
		if(transactionConnection.get() != null) {
			return transactionConnection.get();
		}else {
			synchronized (pooledSource) {
				Connection connection = pooledSource.getConnection();
				addOpenConnection(connection);
				return connection;
			}
		}				
	}
	
	
	/********************************************************************************************
	 * Add a connection that was openend to the list of open connections.
	 * When connections remain after the Servlet returns, they will be closed 
	 * by the RequestHandler using hardCloseRemainingConnections().
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public void forceCloseRemainingConnections() {	
		
		//--------------------------------------
		// Add transaction connection to handling
		if(transactionConnection.get() != null) {
			
			if(myOpenConnections.get() == null) {
				myOpenConnections.set( new ArrayList<Connection>() );
			}
			
			myOpenConnections.get().add( transactionConnection.get() );
			transactionConnection.remove();
		}
		
		//--------------------------------------
		// Return if null
		if(myOpenConnections.get() == null) {
			//all good, return
			return;
		}
		
		ArrayList<Connection> connArray = myOpenConnections.get();
		
		int counter = 0;
		
		//Create new array to avoid ConcurrentModificationException
		for(Connection con : connArray.toArray(new Connection[] {})) {
			
			try {
				if(!con.isClosed()) {
					counter++;
					logger
						.warn("DBInterface.forceCloseRemainingConnections: "+con.getClass());
					con.close();
				}
				connArray.remove(con);
			} catch (SQLException e) {
				logger
					.error("Error on forced closing of DB connection.", e);
			}
		}
		
		if(counter > 0) {
			logger
				.warn(""+counter+" database connection(s) not closed properly.");
		}
	}
	
	/********************************************************************************************
	 * Add a connection that was openend to the list of open connections.
	 * When connections remain after the Servlet returns, they will be closed 
	 * by the RequestHandler using forceCloseRemainingConnections().
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	protected void addOpenConnection(Connection connection) {	
		if(myOpenConnections.get() == null) {
			myOpenConnections.set(new ArrayList<Connection>());
		}
		
		myOpenConnections.get().add(connection);
	}
	
	/********************************************************************************************
	 * Removes a connection that was openend from the list of open connections.
	 * When connections remain after the Servlet returns, they will be closed 
	 * by the RequestHandler using hardCloseRemainingConnections().
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	protected void removeOpenConnection(Connection connection) {	
		
		if(myOpenConnections.get() == null) {
			return;
		}
		myOpenConnections.get().remove(connection);
	}
	
	/********************************************************************************************
	 * Returns if a DB transaction was already started in the current thread.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public boolean transactionIsStarted() {	
		return transactionConnection.get() != null;
	}
	
	/********************************************************************************************
	 * Starts a new transaction.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public void transactionStart() {	
		
		if(transactionConnection.get() != null) {
			logger
				.error("A transaction was already started for this thread. Use commitTransaction() before starting another one.", new Throwable());
			return;
		}
		
		try {
			Connection con = this.getConnection();
			con.setAutoCommit(false);
			transactionConnection.set(con);
			addOpenConnection(con);
			logger.trace("DB transaction started.");
			
		} catch (SQLException e) {
			logger
				.error("Error while retrieving DB connection.", e);
		}
		
	}
	
	/********************************************************************************************
	 * Commits a new transaction.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public void transactionEnd(boolean isSuccess) {
		if(isSuccess) {
			transactionCommit();
		}else {
			transactionRollback();
		}
	}
	/********************************************************************************************
	 * Commits the transaction started with transactionStart.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public void transactionCommit() {	
		
		
		if(transactionConnection.get() == null) {
			logger
				.trace("There is no running transaction. Use beginTransaction() before using commit.");
			return;
		}
		
		Connection con = null;
		
		try {
			con = transactionConnection.get();
			con.commit();
			logger.trace("DB transaction committed.");
		} catch (SQLException e) {
			logger
				.error("Error occured on commit transaction.", e);
		} finally {
			transactionConnection.remove();
			if(con != null) { 
				try {
					removeOpenConnection(con);
					con.setAutoCommit(true);
					con.close();
				} catch (SQLException e) {
					logger
						.error("Error occured closing DB resources.", e);
				}
				
			}
		}
		
	}
	
	/********************************************************************************************
	 * Rollbacks the transaction.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	public void transactionRollback() {	
		
		
		if(transactionConnection.get() == null) {
			logger
				.trace("There is no running transaction. Use beginTransaction() before using commit.");
			return;
		}
		
		Connection con = null;
		
		try {
			con = transactionConnection.get();
			con.rollback();
			logger
				.trace("DB transaction rolled back.");
		} catch (SQLException e) {
			logger
				.error("Error occured on rollback transaction.", e);
		} finally {
			transactionConnection.remove();
			if(con != null) { 
				try {
					con.setAutoCommit(true);
					con.close();
					removeOpenConnection(con);
				} catch (SQLException e) {
					logger
						.error("Error occured closing DB resources.", e);
				}
				
			}
		}
		
	}
	
	/********************************************************************************************
	 * 
	 * @param conn the connection to be closed
	 ********************************************************************************************/
	public void close(Connection conn){
		
		try {
			if(!conn.isClosed()) {
				removeOpenConnection(conn);
				conn.close();
			}
		} catch (SQLException e) {
			logger
				.error("Exception occured while closing connection. ", e);
		}
	}
	/********************************************************************************************
	 * 
	 * @param resultSet which should be closed.
	 ********************************************************************************************/
	public void close(ResultSet resultSet){
		
		try {
			if(resultSet != null 
			&& transactionConnection.get() == null
			&& resultSet.getStatement() != null 
			&& !resultSet.getStatement().isClosed()) {
				
				removeOpenConnection(resultSet.getStatement().getConnection());
				
				if(!resultSet.getStatement().getConnection().isClosed()) {
					resultSet.getStatement().getConnection().close();
					resultSet.close();
				}
			}
		} catch (SQLException e) {
			logger
				.error("Exception occured while closing ResultSet. ", e);
		}
	}
	
	/********************************************************************************************
	 * Closes all connections in the pool.
	 ********************************************************************************************/
	public void closeAll(){
		try {
			pooledSource.close();
		} catch (SQLException e) {
			logger
			.error("Exception occured while closing connection in pool. ", e);
		}
	}
	
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public PFRDBSQLBuilder create(String metricName) {
		return new PFRDBSQLBuilder(this, metricName);
	}
			
	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfaceH2(String servername, int port, String storePath, String databaseName, String username, String password) {
		
		String urlPart = servername+":"+port+"/"+storePath+"/"+databaseName;
		String uniqueName = "H2:"+urlPart;
		String connectionURL = "jdbc:h2:tcp://"+urlPart+";MODE=MYSQL;IGNORECASE=TRUE";
		String driverClass = "org.h2.Driver";

		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password);
		
	}
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfaceH2AutoServer(int port, String storePath, String databaseName, String username, String password) {
		
		String urlPart = storePath+"/"+databaseName;
		String uniqueName = "H2:"+urlPart;
		String connectionURL = "jdbc:h2:"+urlPart+";IGNORECASE=TRUE;AUTO_SERVER=TRUE;AUTO_SERVER_PORT="+port;
		String driverClass = "org.h2.Driver";

		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password);
		
	}
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfaceMySQL(String uniqueNamePrefix, String servername, int port, String dbName, String username, String password) {
		
		
		String urlPart = servername+":"+port+"/"+dbName;
		String uniqueName = uniqueNamePrefix+"MySQL:"+servername+":"+port;
		String connectionURL = "jdbc:mysql://"+urlPart;
		String driverClass = "com.mysql.cj.jdbc.Driver";
		
		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password);
		
	}
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfacePostgres(String uniqueNamePrefix, String servername, int port, String dbName, String username, String password) {
		
		String urlPart = servername+":"+port+"/"+dbName;
		String uniqueName = uniqueNamePrefix+"MySQL:"+servername+":"+port;
		String connectionURL = "jdbc:postgresql://"+urlPart;
		String driverClass = "org.postgresql.Driver";

		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password);
		
	}


	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfaceMSSQL(String uniqueNamePrefix, String servername, int port, String dbName, String username, String password) {
		
		String urlPart = servername+":"+port+";databaseName="+dbName;
		String uniqueName = uniqueNamePrefix+":MSSQL:"+servername+":"+port;
		String connectionURL = "jdbc:sqlserver://"+urlPart;
		String driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		
		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password);
		
	}


	/************************************************************************
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterfaceOracle(String uniqueNamePrefix, String servername, int port, String name, String type, String username, String password) {
		
		String urlPart = "";
		if(type.trim().equals("SID")) {
			//jdbc:oracle:thin:@myHost:myport:sid
			urlPart = servername+":"+port+":"+name;
		}else {
			//jdbc:oracle:thin:@//myHost:1521/service_name
			urlPart = servername+":"+port+"/"+name;
		}

		String uniqueName = uniqueNamePrefix+":Oracle:"+servername+":"+port;
		String connectionURL = "jdbc:oracle:thin:@"+urlPart;
		String driverClass = "oracle.jdbc.OracleDriver";
		String validationQuery = null;
		
		return initDBInterface(
				uniqueName, 
				driverClass, 
				connectionURL, 
				username, 
				password,
				validationQuery);
		
	}
	
	/************************************************************************
	 * Creates a DBInterface with a pooled datasource with a default validation
	 * query 'SELECT 1'.
	 * Adds the connection pool to the Connection pool management.
	 * Sets default connection pool settings.
	 * 
	 * @return DBInterface
	 * 
	 ************************************************************************/
	public static PFRDB initDBInterface(String uniquepoolName, String driverName, String url, String username, String password) {
		return initDBInterface(uniquepoolName, driverName, url, username, password, "SELECT 1");
	}

	/************************************************************************
	 * Creates a DBInterface with a pooled datasource.
	 * Adds the connection pool to the Connection pool management.
	 * Sets default connection pool settings.
	 * 
	 * @return DBInterface
	 * 
	 ************************************************************************/
	@SuppressWarnings("deprecation")
	public static PFRDB initDBInterface(String uniquepoolName, String driverName, String url, String username, String password, String validationQuery) {
		
		BasicDataSource datasource;
		
		try {
			//Driver name com.microsoft.sqlserver.jdbc.SQLServerDriver
			//Connection URL Example: "jdbc:sqlserver://localhost:1433;databaseName=AdventureWorks;user=MyUserName;password=*****;";  
			datasource = new BasicDataSource();
			
			datasource.setDriverClassName(driverName);
			datasource.setUrl(url);	
			
			// try to recover when DB connection was lost
			datasource.setRemoveAbandonedOnBorrow(true);
			datasource.setRemoveAbandonedTimeout(60);
			datasource.setTestOnBorrow(true);
			
			
			if(validationQuery != null) {
				datasource.setValidationQuery(validationQuery);
			}

			datasource.setUsername(username);
			datasource.setPassword(password);
			
			PFRDB.setDefaultConnectionPoolSettings(datasource);
			
			//----------------------------------
			// Test connection
			//pooledSource.setLoginTimeout(5);
			Connection connection = datasource.getConnection();
			connection.close();
			
			PFRDB.registerManagedConnectionPool(uniquepoolName, datasource);
			
		} catch (Exception e) {
			logger
				.error("Exception occured initializing DBInterface.", e);
			return null;
		}
		
		PFRDB db = new PFRDB(datasource);

		logger.info("Created DBInteface: "+ url);
		return db;
	}
	
	/********************************************************************************************
	 * 
	 * @param prepared the prepared statement with placeholders
	 * @param values the values to be placed in the prepared statement. Supports String, Integer,
	 *               Boolean, Float, Date, Timestamp, Blob, Clob, Byte
	 * @throws SQLException 
	 ********************************************************************************************/
	@SuppressWarnings("rawtypes")
	public static void prepareStatement(PreparedStatement prepared, Object... values) throws SQLException{
		
		try {
			if(values != null) {
				for(int i = 1; i <= values.length ; i++) {
					Object currentValue = values[i-1];
					// TODO: Could be a better/faster solution: prepared.setObject(i+1, currentValue);
	
					if		(currentValue instanceof String) 		{ prepared.setString(i, (String)currentValue); }
					else if	(currentValue instanceof StringBuilder) { prepared.setString(i, currentValue.toString() ); }
					else if	(currentValue instanceof char[]) 		{ prepared.setString(i, new String((char[])currentValue)); }
					else if (currentValue instanceof Integer) 		{ prepared.setInt(i, (Integer)currentValue); }
					else if (currentValue instanceof Boolean) 		{ prepared.setBoolean(i, (Boolean)currentValue); }
					else if (currentValue == null) 					{ prepared.setNull(i, Types.NULL); }
					else if (currentValue instanceof Long) 			{ prepared.setLong(i, (Long)currentValue); }
					else if (currentValue instanceof Float) 		{ prepared.setFloat(i, (Float)currentValue); }
					else if (currentValue instanceof BigDecimal) 	{ prepared.setBigDecimal(i, (BigDecimal)currentValue); }
					else if (currentValue instanceof Date) 			{ prepared.setDate(i, (Date)currentValue); }
					else if (currentValue instanceof Timestamp) 	{ prepared.setTimestamp(i, (Timestamp)currentValue); }
					else if (currentValue instanceof Blob) 			{ prepared.setBlob(i, (Blob)currentValue); }
					else if (currentValue instanceof Clob) 			{ prepared.setClob(i, (Clob)currentValue); }
					else if (currentValue instanceof Byte) 			{ prepared.setByte(i, (Byte)currentValue); }
					else if (currentValue instanceof ArrayList) 	{ prepared.setArray(i, prepared.getConnection().createArrayOf("VARCHAR", ((ArrayList)currentValue).toArray() )); }
					else if (currentValue instanceof Integer[]) 		{ prepared.setArray(i, prepared.getConnection().createArrayOf("INTEGER", (Integer[])currentValue)); }
					else if (currentValue instanceof Object[]) 		{ prepared.setArray(i, prepared.getConnection().createArrayOf("VARCHAR", (Object[])currentValue)); }
					//else if (currentValue instanceof LinkedHashMap)	{ prepared.setString(i, (currentValue)); }
					else if (currentValue.getClass().isEnum()) 		{ prepared.setString(i, currentValue.toString());}
					else { throw new RuntimeException("Unsupported database field type: "+ currentValue.getClass().getName());}
				}
			}
		}catch(Exception e){
			//do this to also log below when an error occurs
			throw e;
		}finally {
			logger.trace("Debug: Prepared Statement");
		}

	}
	
	/********************************************************************************************
	 *
	 ********************************************************************************************/
	@SuppressWarnings("deprecation")
	public static void setDefaultConnectionPoolSettings(BasicDataSource pooledSource) {
		pooledSource.setMaxConnLifetimeMillis(60*60*1000);
		pooledSource.setTimeBetweenEvictionRunsMillis(5*60*1000);
		pooledSource.setInitialSize(10);
		pooledSource.setMinIdle(10);
		pooledSource.setMaxIdle(70);
		pooledSource.setMaxTotal(90);
		pooledSource.setMaxOpenPreparedStatements(100);
	}
	
	/********************************************************************************************
	 * Add a connection pool as a managed connection pool.
	 * The connection pool will show up in the Database Analytics.
	 * 
	 ********************************************************************************************/
	public static void registerManagedConnectionPool(String uniqueName, BasicDataSource datasource) {	
		
		if(!managedConnectionPools.containsKey(uniqueName)) {
			managedConnectionPools.put(uniqueName, datasource);
		}else {
			removeManagedConnectionPool(uniqueName);
			managedConnectionPools.put(uniqueName, datasource);
			
			logger.info("A connection pool with the name '"+uniqueName+"' was already registered and was updated.");
		}
		
	}
	
	/********************************************************************************************
	 * Remove connection pool from the managed connection pools.
	 ********************************************************************************************/
	public static void removeManagedConnectionPool(String uniqueName) {	
		BasicDataSource removedPool = managedConnectionPools.remove(uniqueName);	

		try {
			removedPool.close();
		} catch (SQLException e) {
			logger.error("Error closing connection pool: "+e.getMessage(), e);
		}
	}
	
	/********************************************************************************************
	 * Remove connection pool from the managed connection pools.
	 * 
	 * @throws SQLException 
	 ********************************************************************************************/
	@SuppressWarnings("deprecation")
	public static JsonArray getConnectionPoolStatsAsJSON() {	
		
		JsonArray result = new JsonArray();
		for(Entry<String, BasicDataSource> entry : managedConnectionPools.entrySet()) {
			
			JsonObject stats = new JsonObject();
			
			BasicDataSource source = entry.getValue();
			
			stats.addProperty("NAME", entry.getKey());
			stats.addProperty("MAX_CONNECTION_LIFETIME", source.getMaxConnLifetimeMillis());
			stats.addProperty("EVICTION_INTERVAL", source.getTimeBetweenEvictionRunsMillis());
			stats.addProperty("MIN_IDLE_CONNECTIONS", source.getMinIdle());
			stats.addProperty("MAX_IDLE_CONNECTIONS", source.getMaxIdle());
			stats.addProperty("MAX_TOTAL_CONNECTIONS", source.getMaxTotal());
			stats.addProperty("IDLE_COUNT", source.getNumIdle());
			stats.addProperty("ACTIVE_COUNT", source.getNumActive());
			
			result.add(stats);
		}
		
		return result;
	}
	
	
	/********************************************************************************************
	 * Class used to prepare the SQL executions
	 ********************************************************************************************/
	public class PFRDBSQLBuilder {
		
		private String metricName = null;
		private PFRDB db = null;
		
		private boolean enableRangedMetric = false;
		private String rangeName = null;
		private int initialRange = 5;
		
		/********************************************************************************************
		 * 
		 ********************************************************************************************/
		public PFRDBSQLBuilder(PFRDB db, String metricName) {
			this.db = db;
			this.metricName = metricName;
		}
		
		/********************************************************************************************
		 * 
		 ********************************************************************************************/
		public PFRDBSQLBuilder enableRangedMetric(String rangeName, int initialRange) {
			this.enableRangedMetric = true;
			this.rangeName = rangeName;
			this.initialRange = initialRange;
			return this;
		}
		
		/********************************************************************************************
		 * Executes a query using the PreparedStatement.execute() function.
		 * 
		 * @param metricName name of the metric or null if not defined.
		 * @param sql with or without placeholders
		 * @param values the values to be placed in the prepared statement
		 * @return true if update count is > 0, false otherwise
		 ********************************************************************************************/
		public boolean execute(String sql, Object... values){	
	        
			//System.out.println("SQL: "+sql);
			Connection conn = null;
			PreparedStatement prepared = null;

			boolean result = false;
			try {
				//-----------------------------------------
				// Initialize Variables
				conn = db.getConnection();
				
				prepared = conn.prepareStatement(sql);
				
				//-----------------------------------------
				// Prepare Statement
				prepareStatement(prepared, values);
				
				//-----------------------------------------
				// Execute
				HSRRecord r = null;
				if(metricName != null) { HSR.start(metricName); }
					boolean isResultSet = prepared.execute();
				if(metricName != null) { r = HSR.end(); }
				
				
				if(!isResultSet) {
					int updateCount = prepared.getUpdateCount();
					
					if(updateCount > 0) { result = true; }
					
					if(enableRangedMetric && r != null ) {
						HSR.addMetricRanged(
							    metricName + " - " + rangeName
							  , r.value()
							  , updateCount
							  , initialRange
							);
					}
					
				}
				
			} catch (SQLException e) {
				if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
				
				logger.error("Database Error: "+e.getMessage(), e);
			} finally {
				try {
					if(conn != null && transactionConnection.get() == null) { 
						removeOpenConnection(conn);
						conn.close(); 
					}
					if(prepared != null) { prepared.close(); }
				} catch (SQLException e) {
					logger.error("Issue closing resources.", e);
				}
				
			}
			
			logger.trace("SQL Statement: "+sql);
			return result;
		}
		
		/********************************************************************************************
		 * Executes a query using the PreparedStatement.executeBatch() function.
		 * 
		 * @param metricName name of the metric or null if not defined.
		 * @param sql with or without placeholders
		 * @param values the values to be placed in the prepared statement
		 * @param 
		 * @return number of affected rows
		 ********************************************************************************************/
		public int executeBatch(String metricName, String sql, Object... values){	
	        
			Connection conn = null;
			PreparedStatement prepared = null;

			try {
				//-----------------------------------------
				// Initialize Variables
				conn = db.getConnection();
				prepared = conn.prepareStatement(sql);
				
				//-----------------------------------------
				// Prepare Statement
				db.prepareStatement(prepared, values);
				prepared.addBatch();
				
				//-----------------------------------------
				// Execute
				HSRRecord r = null;
				if(metricName != null) { HSR.start(metricName); }
				int[] resultCounts = prepared.executeBatch();
				if(metricName != null) { r = HSR.end(); }
				

				int totalRows = 0;
				for(int i : resultCounts) {
					if(i >= 0) {
						totalRows += i;
					}
				}
				
				if(enableRangedMetric && r != null ) {
					HSR.addMetricRanged(
						    metricName + " - " + rangeName
						  , r.value()
						  , totalRows
						  , initialRange
						);
				}
					
				
				
				return totalRows;
				
			} catch (SQLException e) {
				if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
				
				logger.error("Database Error: "+e.getMessage(), e);
			} finally {
				try {
					if(conn != null && transactionConnection.get() == null) { 
						removeOpenConnection(conn);
						conn.close(); 
					}
					if(prepared != null) { prepared.close(); }
				} catch (SQLException e) {
					logger.error("Issue closing resources.", e);
				}
				
			}
			
			logger.trace("SQL Statement: "+sql);
			return -1;
		}
		
		/********************************************************************************************
		 * Executes the insert and returns the generated Key of the new record. (what is a
		 * primary key in most cases)
		 * 
		 * @param sql string with placeholders
		 * @param generatedKeyName name of the column of the key to retrieve
		 * @param values the values to be placed in the prepared statement
		 * @return generated key, null if not successful
		 ********************************************************************************************/
		public Integer preparedInsertGetKey(String sql, String generatedKeyName, Object... values){	
	        
			Connection conn = null;
			PreparedStatement prepared = null;

			Integer generatedID = null;
			try {
				//-----------------------------------------
				// Initialize Variables
				conn = db.getConnection();
				prepared = conn.prepareStatement(sql, new String[] {generatedKeyName});
				
				//-----------------------------------------
				// Prepare Statement
				prepareStatement(prepared, values);
				
				//-----------------------------------------
				// Execute
				int affectedRows = prepared.executeUpdate();

				if(affectedRows > 0) {
					ResultSet result = prepared.getGeneratedKeys();
					result.next();
					generatedID = result.getInt(generatedKeyName);
				}
				
			} catch (SQLException e) {
				
				logger.error("Database Error: "+e.getMessage(), e);
			} finally {
				try {
					if(conn != null && transactionConnection.get() == null) { 
						removeOpenConnection(conn);
						conn.close(); 
					}
					if(prepared != null) { prepared.close(); }
				} catch (SQLException e) {
					logger.error("Issue closing resources.", e);
				}
				
			}
			
			logger.trace("SQL Statement: "+sql);
			return generatedID;
		}
		/********************************************************************************************
		 * Returns the result or null if there was any issue.
		 * 
		 * @param sql string with placeholders
		 * @param values the values to be placed in the prepared statement
		 * @throws SQLException 
		 ********************************************************************************************/
		public ResultSet preparedExecuteQuery(String sql, Object... values){
			return preparedExecuteQuery(false, sql, values);
		}
		
		/********************************************************************************************
		 * Returns the result or null if there was any issue.
		 * Errors will be written to log but not be propagated to client.
		 * @param sql string with placeholders
		 * @param values the values to be placed in the prepared statement
		 * @throws SQLException 
		 ********************************************************************************************/
		public ResultSet preparedExecuteQuerySilent(String sql, Object... values){
			return preparedExecuteQuery(true, sql, values);
		}
		
		/********************************************************************************************
		 * Returns the result or null if there was any issue.
		 * 
		 * @param isSilent write errors to log but do not propagate to client
		 * @param sql string with placeholders
		 * @param values the values to be placed in the prepared statement
		 * @throws SQLException 
		 ********************************************************************************************/
		private ResultSet preparedExecuteQuery(boolean isSilent, String sql, Object... values){	
	        		
			Connection conn = null;
			PreparedStatement prepared = null;
			ResultSet result = null;
			try {
				//-----------------------------------------
				// Initialize Variables
				conn = db.getConnection();
				prepared = conn.prepareStatement(sql);
				
				//-----------------------------------------
				// Prepare Statement
				PFRDB.prepareStatement(prepared, values);
				
				//-----------------------------------------
				// Execute
				result = prepared.executeQuery();
				
			} catch (SQLException e) {
				
				logger.error("Issue executing prepared statement: "+e.getLocalizedMessage(), e);
				try {
					if(conn != null && transactionConnection.get() == null) { 
						removeOpenConnection(conn);
						conn.close(); 
					}
					if(prepared != null) { prepared.close(); }
				} catch (SQLException e2) {
					logger.error("Issue closing resources.", e2);
				}
			} 
			
			logger.trace("SQL Statement: "+sql);
					 
			return result;
		}

	}

}
