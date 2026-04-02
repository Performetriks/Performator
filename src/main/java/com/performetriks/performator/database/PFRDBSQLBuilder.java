package com.performetriks.performator.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRExpression.Operator;
import com.xresch.hsr.stats.HSRRecord;
import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;
import com.xresch.hsr.stats.HSRSLA;
import com.xresch.xrutils.base.XR;
import com.xresch.xrutils.data.XRRecord;
import com.xresch.xrutils.database.XRResultSetAsJsonReader;
import com.xresch.xrutils.database.XRResultSetUtils;

/********************************************************************************************
 * Class used to prepare the SQL executions
 ********************************************************************************************/
public class PFRDBSQLBuilder {
	
	static Logger logger = LoggerFactory.getLogger(PFRDBSQLBuilder.class.getName());
	
	private String metricName = null;
	private PFRDB db = null;
	
	private boolean enableRangedMetric = false;
	private String rangeName = null;
	private int initialRange = 5;
	
	private HSRSLA sla = null;
	
	private ResultSet result = null;
	
	/********************************************************************************************
	 * 
	 ********************************************************************************************/
	public PFRDBSQLBuilder(PFRDB db, String metricName) {
		this.db = db;
		this.metricName = metricName;
	}
	
	
	/***************************************************************************
	 * Set an SLA for this SQL. You can only set one SLA per SQL.
	 ***************************************************************************/
	public PFRDBSQLBuilder sla(HSRSLA sla) {
		this.sla = sla;
		return this;
	}
	
	/***************************************************************************
	 * Set an SLA for this SQL. You can only set one SLA per SQL.
	 ***************************************************************************/
	public PFRDBSQLBuilder sla(HSRMetric metric, Operator operator, int value) {
		this.sla = new HSRSLA(metric, operator, value);
		return this;
	}
	
	/***************************************************************************
	 * Set an SLA for this SQL. You can only set one SLA per SQL.
	 ***************************************************************************/
	public PFRDBSQLBuilder sla(HSRMetric metric, Operator operator, Number value) {
		this.sla = new HSRSLA(metric, operator, value);
		return this;
	}
	
	/***************************************************************************
	 * Set an SLA for this SQL. You can only set one SLA per SQL.
	 ***************************************************************************/
	public PFRDBSQLBuilder sla(HSRMetric metric, Operator operator, BigDecimal value) {
		this.sla = new HSRSLA(metric, operator, value);
		return this;
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
			HSR.start(finalName, sla); 
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
			
			handleException(prepared, e);
		} finally {
			doClose(conn, prepared);
		}
		
		logger.trace("SQL Statement: "+sql);
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
			HSR.start(finalName, sla); 
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
			
			handleException(prepared, e);
		} finally {
			doClose(conn, prepared);
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
			HSR.start(finalName, sla); 
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
			
			handleException(prepared, e);
		} finally {
			doClose(conn, prepared);
		}
		
		logger.trace("SQL Statement: "+sql);
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
			HSR.start(finalName, sla); 
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
			
			if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
			
			handleException(prepared, e);
			doClose(conn, prepared);
		} 
		
		logger.trace("SQL Statement: "+sql);
		return this;
	}

	/********************************************************************************************
	 * Returns the result or null if there was any issue.
	 * 
	 * @param prepared
	 * @param exception
	 * 
	 ********************************************************************************************/
	private void handleException(PreparedStatement prepared, SQLException e) {
		
		if(prepared != null) {
			logger.error("Exception during DB call: \""+e.getLocalizedMessage()+"\" For Statement: "+prepared.toString(), e);
		}else {
			logger.error("Exception during DB call: \""+e.getLocalizedMessage(), e);
		}
	}
	/********************************************************************************************
	 * Closes Connections and such.
	 * 
	 * @param conn
	 * @param prepared
	 ********************************************************************************************/
	private void doClose(Connection conn, PreparedStatement prepared) {
		
		try {
			if(conn != null && db.transactionConnection.get() == null) { 
				db.removeOpenConnection(conn);
				conn.close(); 
			}
			if(prepared != null) { prepared.close(); }
		} catch (SQLException e) {
			logger.error("Error while closing DB connection: "+e.getMessage(), e);
		}
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
			HSR.start(finalName + " [EXEC]", sla); 
				result = prepared.executeQuery();
			HSRRecord recordExec = HSR.end();
			
			//-----------------------------------------
			// 
			HSRRecord recordFetch = null;

			int resultSize = 0;
			int jitPreventionCounter = 0;
			if(result != null) {
				
				int columns = result.getMetaData().getColumnCount();
				
				HSR.start(finalName + " [FETCH]", sla); 
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
			if(metricName != null) { HSR.end(false, ""+e.getErrorCode() ); }
			handleException(prepared, e);
		} finally {
			close();
		}
		
		logger.trace("SQL Statement: "+sql);

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
	 * Converts a ResultSet into an array list of XRRecords.
	 * This method closes the result set.
	 * 
	 * @return list of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public ArrayList<XRRecord> toXRRecordList() {
		return XRResultSetUtils.toRecordList(result);
	}
	

	/********************************************************************************************
	 * Converts a ResultSet into a map of Keys and XRRecords.
	 * This method closes the result set.
	 * 
	 * @param result set
	 * @param keyColumnName name of the column that should be used as the key
	 * @return map of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<String, XRRecord> toKeyXRRecordMap(String keyColumnName){	
		return XRResultSetUtils.toKeyRecordMap(result, keyColumnName);
	}
	
	/********************************************************************************************
	 * Converts a ResultSet into a map of IDs and XRRecords.
	 * This method closes the result set.
	 * 
	 * @param result set
	 * @param idColumnName name of the column that should be used as the id
	 * @return map of object, empty if results set is null or an error occurs.
	 ********************************************************************************************/
	public LinkedHashMap<Integer, XRRecord> toIdXRRecordMap(String idColumnName){	
		return XRResultSetUtils.toIdRecordMap(result, idColumnName);
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
	public XRResultSetAsJsonReader toJSONReader() {
		return XRResultSetUtils.toJSONReader(result);
	}
	
	
	/********************************************************************************************
	 * Closes the result set associated  with this SQL Builder.
	 ********************************************************************************************/
	public void close(){	
		XRResultSetUtils.close(result);
	}

}