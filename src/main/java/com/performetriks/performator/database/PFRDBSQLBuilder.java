package com.performetriks.performator.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.google.gson.JsonArray;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord;
import com.xresch.xrutils.base.XR;
import com.xresch.xrutils.data.Unrecord;
import com.xresch.xrutils.database.XRResultSetUtils;
import com.xresch.xrutils.database.XRResultSetUtils.ResultSetAsJsonReader;

/********************************************************************************************
 * Class used to prepare the SQL executions
 ********************************************************************************************/
public class PFRDBSQLBuilder {
	
	/**
	 * 
	 */
	private String metricName = null;
	private PFRDB db = null;
	
	private boolean enableRangedMetric = false;
	private String rangeName = null;
	private int initialRange = 5;
	
	private ResultSet result = null;
	
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
	public PFRDBSQLBuilder enableRangedMetrics(String rangeName, int initialRange) {
		this.enableRangedMetric = true;
		this.rangeName = rangeName;
		this.initialRange = initialRange;
		return this;
	}
	
	/********************************************************************************************
	 * Executes any type of SQL, query, update, delete etc...
	 * Does only return true if there was an update count, does always return false if you execute
	 * a query.
	 * 
	 * @param sql with or without placeholders
	 * @param values the values to be placed in the prepared statement
	 * 
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
			PFRDB.prepareStatement(prepared, values);
			
			//-----------------------------------------
			// Execute
			String finalName = (metricName != null) ? metricName : sql;
			HSR.start(finalName); 
				boolean isResultSet = prepared.execute();
			HSRRecord r = HSR.end();
			
			if(!isResultSet) {
				int updateCount = prepared.getUpdateCount();
				
				if(updateCount > 0) { result = true; }
				
				//-----------------------------------------
				// Ranged Metric
				if(enableRangedMetric && r != null ) {
					HSR.addMetricRanged(
						    finalName + " - " + rangeName
						  , r.value()
						  , updateCount
						  , initialRange
						);
				}
				
			}
			
		} catch (SQLException e) {
			if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
			
			PFRDB.logger.error("Database Error: "+e.getMessage(), e);
		} finally {
			try {
				if(conn != null && db.transactionConnection.get() == null) { 
					db.removeOpenConnection(conn);
					conn.close(); 
				}
				if(prepared != null) { prepared.close(); }
			} catch (SQLException e) {
				PFRDB.logger.error("Issue closing resources.", e);
			}
			
		}
		
		PFRDB.logger.trace("SQL Statement: "+sql);
		return result;
	}
	
	/********************************************************************************************
	 * Executes an update query using the PreparedStatement.execute() function.
	 * 
	 * @param sql with or without placeholders
	 * @param values the values to be placed in the prepared statement
	 * @return true if update count is > 0, false otherwise
	 ********************************************************************************************/
	public boolean update(String sql, Object... values){	
		return execute(sql, values);
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
	public int batch(String metricName, String sql, Object... values){	
        
		Connection conn = null;
		PreparedStatement prepared = null;

		try {
			//-----------------------------------------
			// Initialize Variables
			conn = db.getConnection();
			prepared = conn.prepareStatement(sql);
			
			//-----------------------------------------
			// Prepare Statement
			PFRDB.prepareStatement(prepared, values);
			prepared.addBatch();
			
			//-----------------------------------------
			// Execute
			String finalName = (metricName != null) ? metricName : sql;
			HSR.start(finalName); 
				int[] resultCounts = prepared.executeBatch();
			HSRRecord r = HSR.end();

			int totalRows = 0;
			for(int i : resultCounts) {
				if(i >= 0) {
					totalRows += i;
				}
			}
			
			//-----------------------------------------
			// Ranged Metric
			if(enableRangedMetric && r != null ) {
				HSR.addMetricRanged(
					    finalName + " - " + rangeName
					  , r.value()
					  , totalRows
					  , initialRange
					);
			}
				
			return totalRows;
			
		} catch (SQLException e) {
			if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
			
			PFRDB.logger.error("Database Error: "+e.getMessage(), e);
		} finally {
			try {
				if(conn != null && db.transactionConnection.get() == null) { 
					db.removeOpenConnection(conn);
					conn.close(); 
				}
				if(prepared != null) { prepared.close(); }
			} catch (SQLException e) {
				PFRDB.logger.error("Issue closing resources.", e);
			}
			
		}
		
		PFRDB.logger.trace("SQL Statement: "+sql);
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
	public Integer insertGetKey(String sql, String generatedKeyName, Object... values){	
        
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
			PFRDB.prepareStatement(prepared, values);
			
			
			//-----------------------------------------
			// Execute
			String finalName = (metricName != null) ? metricName : sql;
			HSR.start(finalName); 
				int affectedRows = prepared.executeUpdate();
			HSRRecord r = HSR.end();
			
			if(affectedRows > 0) {
				ResultSet result = prepared.getGeneratedKeys();
				result.next();
				generatedID = result.getInt(generatedKeyName);
			}
			
			//-----------------------------------------
			// Ranged Metric
			if(enableRangedMetric && r != null ) {
				HSR.addMetricRanged(
					    finalName + " - " + rangeName
					  , r.value()
					  , affectedRows
					  , initialRange
					);
			}
			
		} catch (SQLException e) {
			if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
			
			PFRDB.logger.error("Database Error: "+e.getMessage(), e);
		} finally {
			try {
				if(conn != null && db.transactionConnection.get() == null) { 
					db.removeOpenConnection(conn);
					conn.close(); 
				}
				if(prepared != null) { prepared.close(); }
			} catch (SQLException e) {
				PFRDB.logger.error("Issue closing resources.", e);
			}
			
		}
		
		PFRDB.logger.trace("SQL Statement: "+sql);
		return generatedID;
	}

	
	/********************************************************************************************
	 * Returns the result or null if there was any issue.
	 * 
	 * @param isSilent write errors to log but do not propagate to client
	 * @param sql string with placeholders
	 * @param values the values to be placed in the prepared statement
	 * @return 
	 * @throws SQLException 
	 ********************************************************************************************/
	public PFRDBSQLBuilder query(String sql, Object... values){	
        		
		Connection conn = null;
		PreparedStatement prepared = null;

		try {
			//-----------------------------------------
			// Initialize Variables
			conn = db.getConnection();
			prepared = conn.prepareStatement(sql, 
					  ResultSet.TYPE_SCROLL_INSENSITIVE, 
					  ResultSet.CONCUR_READ_ONLY);
			
			//-----------------------------------------
			// Prepare Statement
			PFRDB.prepareStatement(prepared, values);
			
			//-----------------------------------------
			// Execute
			String finalName = (metricName != null) ? metricName : sql;
			HSR.start(finalName); 
				result = prepared.executeQuery();
			HSRRecord r = HSR.end();
			
			result.last();
		    int resultSize = result.getRow();
		    result.beforeFirst();
			
			//-----------------------------------------
			// Ranged Metric
			if(enableRangedMetric && r != null ) {
				HSR.addMetricRanged(
					    finalName + " - " + rangeName
					  , r.value()
					  , resultSize
					  , initialRange
					);
			}
			
		} catch (SQLException e) {
			
			PFRDB.logger.error("Issue executing prepared statement: "+e.getLocalizedMessage(), e);
			try {
				if(conn != null && db.transactionConnection.get() == null) { 
					db.removeOpenConnection(conn);
					conn.close(); 
				}
				if(prepared != null) { prepared.close(); }
			} catch (SQLException e2) {
				PFRDB.logger.error("Issue closing resources.", e2);
			}
		} 
		
		PFRDB.logger.trace("SQL Statement: "+sql);
		return this;
	}
			
	/********************************************************************************************
	 * Returns the result or null if there was any issue.
	 * 
	 * @param isSilent write errors to log but do not propagate to client
	 * @param sql string with placeholders
	 * @param values the values to be placed in the prepared statement
	 * @throws SQLException 
	 ********************************************************************************************/
	public void queryAndRead(String sql, Object... values){	
        		
		Connection conn = null;
		PreparedStatement prepared = null;
		
		try {
			//-----------------------------------------
			// Initialize Variables
			conn = db.getConnection();
			conn.setAutoCommit(false); // enable streaming
			
			prepared = conn.prepareStatement(sql);
			prepared.setFetchSize(100); // enable streaming
			
			//-----------------------------------------
			// Prepare Statement
			PFRDB.prepareStatement(prepared, values);

			//-----------------------------------------
			// Execute
			String finalName = (metricName != null) ? metricName : sql;
			HSR.start(finalName + " [EXEC]"); 
				result = prepared.executeQuery();
			HSRRecord recordExec = HSR.end();
			
			//-----------------------------------------
			// 
			HSRRecord recordFetch = null;

			int resultSize = 0;
			int jitPreventionCounter = 0;
			if(result != null) {
				
				int columns = result.getMetaData().getColumnCount();
				
				HSR.start(finalName + " [FETCH]"); 
					while(result.next()) {
						resultSize++;
						for(int i = 1; i <= columns; i++) {
							jitPreventionCounter += 
									(result.getObject(i) != null) ? 1 : 0;
						}
					}
				recordFetch = HSR.end();
			}
			
			// IMPORTANT: prevent JIT optimization
			PFRDB.SINK = jitPreventionCounter;
			
			//-----------------------------------------
			// Ranged Metric [EXEC]
			if(enableRangedMetric && recordExec != null ) {
				HSR.addMetricRanged(
					    finalName + " [EXEC] - " + rangeName
					  , recordExec.value()
					  , resultSize
					  , initialRange
					);
			}
			
			//-----------------------------------------
			// Ranged Metric [FETCH]
			if(enableRangedMetric && recordFetch != null ) {
				HSR.addMetricRanged(
					    finalName + " [FETCH] - " + rangeName
					  , recordFetch.value()
					  , resultSize
					  , initialRange
					);
			}
			

		} catch (SQLException e) {
			PFRDB.logger.error("Issue executing prepared statement: "+e.getLocalizedMessage(), e);
		} finally {
			close();
		}
		
		PFRDB.logger.trace("SQL Statement: "+sql);

	}
	
	/********************************************************************************************
	 * Executes the query and returns the first value of the first
	 * column as integer. Closes the result set.
	 * Useful for getting counts, averages, maximum etc...
	 * @return integer value, 0 if no rows are selected, null in case of errors
	 ********************************************************************************************/
	public Integer getFirstAsInteger(){	
		return XRResultSetUtils.getFirstAsInteger(result);
	}
	
	/********************************************************************************************
	 * Returns the result set.
	 ********************************************************************************************/
	public ResultSet toResultSet(){	
		return result;
	}
	
	/********************************************************************************************
	 * Converts the ResultSet into a CSV string.
	 ********************************************************************************************/
	public String toCSV(String delimiter){	
		return XRResultSetUtils.toCSV(result, delimiter);
	}
	
	/********************************************************************************************
	 * Converts the ResultSet into a XML string.
	 ********************************************************************************************/
	public String toXML(){	
		return XRResultSetUtils.toXML(result);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into an array list of Unrecords.
	 * This method closes the result set.
	 * 
	 * @return list of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public ArrayList<Unrecord> toUnrecordList() {
		return XRResultSetUtils.toUnrecordList(result);
	}
	

	/********************************************************************************************
	 * Converts a ResultSet into a map of Keys and Unrecords.
	 * This method closes the result set.
	 * 
	 * @param result set
	 * @param keyColumnName name of the column that should be used as the key
	 * @return map of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<String, Unrecord> toKeyUnrecordMap(String keyColumnName){	
		return XRResultSetUtils.toKeyUnrecordMap(result, keyColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map of IDs and Unrecords.
	 * This method closes the result set.
	 * 
	 * @param result set
	 * @param idColumnName name of the column that should be used as the id
	 * @return map of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<Integer, Unrecord> toIdUnrecordMap(String idColumnName){	
		return XRResultSetUtils.toIdUnrecordMap(result, idColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map with the key/values of the selected columns.
	 * @return list of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<Integer, Object> toIDValueMap(Object idColumnName, Object valueColumnName) {
		return XRResultSetUtils.toIDValueMap(result, idColumnName, valueColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map with the key/values of the selected columns.
	 * @return list of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public HashMap<Object, Object> toKeyValueMap(String keyColumnName, String valueColumnName) {
		return XRResultSetUtils.toKeyValueMap(result, keyColumnName, valueColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map with the key/values of the selected columns.
	 * This method closes the result set.
	 * @return map of objects, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<String, String> toKeyValueMapString(String keyColumnName, String valueColumnName) {
		return XRResultSetUtils.toKeyValueMapString(result, keyColumnName, valueColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a list of maps with key/values.
	 * This method closes the result set.
	 *
	 * @return list of maps holding key(column name) with values
	 ********************************************************************************************/
	public ArrayList<LinkedHashMap<String, Object>> toListOfKeyValueMaps() {
		return XRResultSetUtils.toListOfKeyValueMaps(result);
	}
	
	/********************************************************************************************
	 * Execute the Query and gets the values of the specified 
	 * column as a string array.
	 * This method closes the results set.
	 * 
	 * @param columnName
	 * @return string array
	 ********************************************************************************************/
	public String[] toStringArray(String columnName) {
		return XRResultSetUtils.toStringArray(result, columnName);
	}
	
	/********************************************************************************************
	 * Execute the Query and gets the values of the specified 
	 * column as an ArrayList of strings.
	 * This method closes the results set.
	 * 
	 * @param columnName
	 * @return ArrayList of strings
	 ********************************************************************************************/
	public ArrayList<String> toStringArrayList(String columnName) {
		
		return XRResultSetUtils.toStringArrayList(result, columnName);
	}
	
	
	/********************************************************************************************
	 * Execute the Query and gets the values of the specified 
	 * column as an ArrayList of integers.
	 * This method closes the results set.
	 * 
	 * @param result
	 * @param columnName
	 * @return ArrayList of integers
	 ********************************************************************************************/
	public ArrayList<Integer> toIntegerArrayList(String columnName) {
		return XRResultSetUtils.toIntegerArrayList(result, metricName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map with the key/values of the selected columns.
	 * This method closes the result set.
	 * 
	 * @param keyColumnName the column containing the keys 
	 * @param valueColumnName the column containing the values
	 * @return map of objects, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<Object, Object> toLinkedHashMap(Object keyColumnName, Object valueColumnName) {
		return XRResultSetUtils.toLinkedHashMap(result, keyColumnName, valueColumnName);
	}
	
	/********************************************************************************************
	 * Returns a jsonString with an array containing a json object for each row.
	 * Returns an empty array in case of error.
	 * 
	 ********************************************************************************************/
	public String toJSON() {
		return XR.JSON.toJSON(result);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a JsonArray.
	 * This method closes the result set.
	 * 
	 * @return list of maps holding key(column name) with values
	 ********************************************************************************************/
	public JsonArray toJSONArray() {
		return XRResultSetUtils.toJSONArray(result);
	}
	
	
	/********************************************************************************************
	 * Returns a ResultSetAsJsonReader to convert SQL records to json objects one by one. 
	 * ResultSetAsJsonReader will close the result set when fully read to reader.next() == null.
	 * 
	 * @return ResultSetAsJsonReader
	 ********************************************************************************************/
	public ResultSetAsJsonReader toJSONReader() {
		return XRResultSetUtils.toJSONReader(result);
	}
	
	
	/********************************************************************************************
	 * Closes the result set associated  with this SQL Builder.
	 ********************************************************************************************/
	public void close(){	
		XRResultSetUtils.close(result);
	}

}