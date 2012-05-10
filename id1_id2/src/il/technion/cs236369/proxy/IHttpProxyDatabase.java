package il.technion.cs236369.proxy;


public interface IHttpProxyDatabase {

	/**
	 * Adds an entry to the db. IMPORTANT: an update occurs if the url already exist
	 * @param url The request url
	 * @param headers The response headers
	 * @param body The response body
	 * @throws DbException In any case of an error, this exception is thrown, and an 
	 * informative msg should be printed to System.err 
	 */
	public void addToDb(String url, String headers, String body) throws DbException;
	
	/**
	 * Removes the entry from the db
	 * @param url The request url
	 * @throws DbException In any case of an error, this exception is thrown, and an 
	 * informative msg should be printed to System.err 
	 */
	public void removeFromDb(String url) throws DbException;
	
	/**
	 * Returns the headers as a string (exactly how u received them)
	 * @param url The request url
	 * @return The corresponding headers
	 * @throws DbException In any case of an error, this exception is thrown, and an 
	 * informative msg should be printed to System.err 
	 */
	public String getHeaders(String url) throws DbException;
	
	/**
	 * Returns the response body as a string (exactly how u received them)
	 * @param url The request url
	 * @return The corresponding body
	 * @throws DbException In any case of an error, this exception is thrown, and an 
	 * informative msg should be printed to System.err 
	 */
	public String getBody(String url) throws DbException;
	
	/**
	 * A boolean function checking if an entry for url already exists in the db
	 * @param url The request url
	 * @return True - if an entry exists, False - otherwise
	 * @throws DbException In any case of an error, this exception is thrown, and an 
	 * informative msg should be printed to System.err 
	 */
	public boolean contains(String url) throws DbException; 
}
