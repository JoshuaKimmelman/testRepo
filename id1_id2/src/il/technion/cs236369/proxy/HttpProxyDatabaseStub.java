package il.technion.cs236369.proxy;

import java.util.HashSet;
import java.util.Set;

public class HttpProxyDatabaseStub implements IHttpProxyDatabase{
	
	Set<Entry> db;


	public HttpProxyDatabaseStub(String dbURL, String dbName, String tblName, String dbUsername, String dbPassword, String dbDriver){
		db = new HashSet<Entry>();
	}
	
	public void addToDb(String url, String headers, String body) {
		Entry e = new Entry(url, headers, body);
		if (contains(e.url))
			removeFromDb(e.url);
		db.add(e);	
	}
	
	public void removeFromDb(String url) {
		db.remove(new Entry(url,"",""));
	}
	
	public String getHeaders(String url) throws DbException {
		for (Entry e : db) {
			if (e.url.equals(url))
				return e.headers;
		}
		throw new DbException();
	}
	
	public String getBody(String url) throws DbException {
		for (Entry e : db) {
			if (e.url.equals(url))
				return e.body;
		}
		throw new DbException();
	}
	

	public boolean contains(String url) {
		for (Entry e : db) {
			if (e.url.equals(url))
				return true;
		}
		return false;

	}
}

class Entry {

	String body;
	String headers;
	String url;

	public Entry(String url, String headers, String body){
		this.url = url;
		this.headers = headers;
		this.body = body;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Entry))
			return false;
		if (((Entry)obj).url.equals(url))
			return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		return url.hashCode();
	}

}
