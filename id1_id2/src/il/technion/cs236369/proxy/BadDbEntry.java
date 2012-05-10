package il.technion.cs236369.proxy;

public class BadDbEntry extends Exception {

	String key;
	
	/**
	 * @param uri Url of bad entry
	 */
	public BadDbEntry(String uri) {
		key = uri;
	}

	/**
	 * @return The key of the bad entry
	 */
	public String getUrl(){
		return key;
	}
	
	/**
	 * Thrown in case of a bad entry in db
	 */
	private static final long serialVersionUID = 7702169661805355224L;

}
