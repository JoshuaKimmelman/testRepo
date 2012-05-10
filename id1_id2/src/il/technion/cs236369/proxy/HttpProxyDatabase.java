package il.technion.cs236369.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class HttpProxyDatabase implements IHttpProxyDatabase {
	private String dbDriver;
	private String dbUsername;
	private String dbPassword;
	private String tblName;
	private String dbName;
	private String dbURL;

	public HttpProxyDatabase(String dbURL, String dbName, String tblName, String dbUsername, String dbPassword, String dbDriver) {
		this.dbURL = dbURL;
		this.dbName = dbName;
		this.tblName = tblName;
		this.dbUsername = dbUsername;
		this.dbPassword = dbPassword;
		this.dbDriver = dbDriver;
	}
	
	ResultSet executeQuery(String query, Connection con, Statement stmt, ResultSet rs) throws ClassNotFoundException, SQLException {
		con = null;
		stmt = null;
		rs = null;
		Class.forName(dbDriver);
		con = DriverManager.getConnection(dbURL + dbName, dbUsername, dbPassword);
		stmt = con.createStatement();
		rs = stmt.executeQuery(query);
		return rs;
	}

	@Override
	public void addToDb(String url, String headers, String body) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeFromDb(String url) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getHeaders(String url) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getBody(String url) {
		// TODO Auto-generated method stub
		return null;
	}
}
