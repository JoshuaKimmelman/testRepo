package il.technion.cs236369.proxy;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BasicHeaderValueFormatter;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.HeaderValueFormatter;
import org.apache.http.message.HeaderValueParser;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

/**
 * Handles requests received from the client.
 * @author Yehoshua
 *
 */
class HttpProxyRequestHandler implements HttpRequestHandler  {
	SocketFactory sockFactory;
	IHttpProxyDatabase db;
	
	
    /**
     * Constructor
     * @param sockFactory The socket factory to be used when creating new sockets
     * @param db The cache databas
     */
    public HttpProxyRequestHandler(SocketFactory sockFactory, IHttpProxyDatabase db) {
    	super();
    	this.db = db;
    	this.sockFactory = sockFactory;
    }

    /* (non-Javadoc)
     * @see org.apache.http.protocol.HttpRequestHandler#handle(org.apache.http.HttpRequest, org.apache.http.HttpResponse, org.apache.http.protocol.HttpContext)
     */
    public void handle(final HttpRequest request, final HttpResponse response, final HttpContext context){
		     		
		HttpProxy.printMsg("request:");

		//This is the decision tree
		request.removeHeaders("Accept-Encoding"); 
		//request.removeHeaders("If-Modified-Since"); 
		HttpProxy.printMsg("\t" +request.toString());
		
		try {
			if (!checkNoCache(request.getHeaders("Cache-Control"))) {
				HttpProxy.printMsg("\t" + "Request cacheable");
				if (inCache(request)) {
					HttpProxy.printMsg("\t" + "Request is in cache");
					HttpResponse cacheResponse = validateCache(request);
					if (isValid(cacheResponse)) {
						HttpProxy.printMsg("\t" + "Cache is valid");
						getResponseFromCache(request, response);
					} else {
						HttpProxy.printMsg("\t" + "Cache is NOT valid");
						removeFromCache(request);
						addToCache(request,cacheResponse);
						setResponse(cacheResponse,response);
					}
				} else {
					HttpProxy.printMsg("\t" + "Request is NOT in cache");
					sendRequest(request, response);
					addToCache(request,response);
				}
			} else {
				HttpProxy.printMsg("\t" + "Request NOT cacheable");
				sendRequest(request, response);
				if (inCache(request)) {
					HttpProxy.printMsg("\t" + "Request is in cache");
					removeFromCache(request);
				} else {
					HttpProxy.printMsg("\t" + "Request is NOT in cache");
				}
				addToCache(request,response);
			}
		} catch (DbException e) {
			HttpProxy.printErr("Db error, ignoring cache.");
			IgnoreCache(request, response);
		} catch (CacheValidationException e) {
			HttpProxy.printErr("Cache validation error, ignoring cache.");
			IgnoreCache(request, response);
		} catch (BadDbEntry e) {
			HttpProxy.printErr("Bad db entry, ignoring cache.");
			BadDbEntryExcptionHandler(request, response, e);
		} catch (Internal500Exception e) {
			HttpProxy.printErr("500 Internal Error");
			HttpProxy.set500Response(response);
		} catch (Exception e) {
			HttpProxy.printErr("Unknown error, ignoring cache.");
			IgnoreCache(request, response);
		}
    	
    	HttpProxy.printMsg("response:");
    	HttpProxy.printMsg("\t" + response.getStatusLine().getStatusCode() + "");
    	if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
    		HttpProxy.printMsg("\t" + response.getStatusLine().getReasonPhrase());
    	}
    }

	/**
	 * Handler to be used in case a bad db entry exception is caught in the main handle function
	 * @param request The request received from the client
	 * @param response The response to be delivered back to the client
	 * @param e
	 */
	void BadDbEntryExcptionHandler(HttpRequest request,
			HttpResponse response, BadDbEntry e) {
		try {
			db.removeFromDb(e.getUrl()); 
		} catch (Exception e1) {}
		IgnoreCache(request, response);
	}

	/**
	 * Ignores cache and just forwards the request as is
	 * @param request The request received from the client
	 * @param response The response to be delivered back to the client
	 */
	void IgnoreCache(HttpRequest request, HttpResponse response) {
		try {
			sendRequest(request, response);
			addToCache(request, response);
		} catch (Internal500Exception e) {
			HttpProxy.printErr("500 Internal Error");
			HttpProxy.set500Response(response);
		} catch (DbException e) { }
	}

	/**
	 * Validates the cache entry for the specified request (by sending a get with "If-Modified-Since")
	 * @param request The request received from the client
	 * @return The response received from the destination server
	 * @throws CacheValidationException Throw in case of an error while trying to validate the cache
	 * @throws DbException Thrown in case of a database error
	 * @throws BadDbEntry Thrown in cas a bad entry is discovered in the DB
	 */
	HttpResponse validateCache(HttpRequest request) throws CacheValidationException, DbException, BadDbEntry {
		try {
			HttpResponse res = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "");
			HttpRequest req = new BasicHttpRequest(request.getRequestLine());
			req.setHeaders(request.getAllHeaders());
			req.addHeader("If-Modified-Since", getModifiedSince(request));
			sendRequest(req, res);
			return res;
		} catch (Internal500Exception e) {
			HttpProxy.printErr("Couldn't validate cache.");
			throw new CacheValidationException();
		}
		
	}

	/**
	 * Returns the "Modified-Since" header value in a response (retrieved from the cache)
	 * @param request The request received from the client
	 * @return The string value of the header
	 * @throws DbException Thrown in case of a database error
	 * @throws BadDbEntry Thrown in case a bad entry is discovered in the DB
	 */
	String getModifiedSince(HttpRequest request) throws DbException, BadDbEntry {
		Header[] headers = parseHeaders(db.getHeaders(request.getRequestLine().getUri()));
		for (Header h : headers) {
			if (h.getName().equals("Last-Modified"))
				return h.getValue();
		}
		throw new BadDbEntry(request.getRequestLine().getUri());
	}

	/**
	 * Copies the src response to the dst response
	 * @param srcResponse The response to be copied 
	 * @param dstResponse The response to copy to
	 * @throws Internal500Exception Thrown in case of a fatal error causing an 500 response to be sent back.
	 */
	void setResponse(HttpResponse srcResponse, HttpResponse dstResponse) throws Internal500Exception {
		try {
			dstResponse.setStatusLine(srcResponse.getStatusLine());
			if (srcResponse.getEntity() != null)
				dstResponse.setEntity(new ByteArrayEntity(EntityUtils.toByteArray(srcResponse.getEntity())));
			else
				dstResponse.setEntity(new StringEntity(""));
			dstResponse.setHeaders(srcResponse.getAllHeaders());
			dstResponse.setLocale(srcResponse.getLocale());
			dstResponse.setParams(srcResponse.getParams());
			dstResponse.setStatusCode(srcResponse.getStatusLine().getStatusCode());
			dstResponse.setReasonPhrase(srcResponse.getStatusLine().getReasonPhrase());
		} catch (IOException e) {
			HttpProxy.printErr("Couldn't forward message. IO malfunction.");
			throw new Internal500Exception();
		}
		
	}

	/**
	 * Rmoves the cache entry from the db for the corresponding request 
	 * @param request A request to be deleted from the db
	 * @throws DbException Thrown in case of a database error
	 */
	void removeFromCache(HttpRequest request) throws DbException {
		db.removeFromDb(request.getRequestLine().getUri());
		
	}

	/**
	 * Sets the response to the request from the cache db
	 * @param request The request received from the client
	 * @param response The response to be delivered back to the client
	 * @throws DbException Thrown in case of a database error
	 * @throws BadDbEntry Thrown in case a bad entry is discovered in the DB
	 */
	void getResponseFromCache(HttpRequest request, HttpResponse response) throws BadDbEntry, DbException {
		try {
			response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
			response.setHeaders(parseHeaders(db.getHeaders(request.getRequestLine().getUri())));
			response.setEntity(new StringEntity(db.getBody(request.getRequestLine().getUri())));
		} catch (UnsupportedEncodingException e) {
			HttpProxy.printErr("Body of cache is using an unsupported encoding");
			throw new BadDbEntry(request.getRequestLine().getUri());
		}
	}

	/**
	 * Parses a string of headers (delimited by \\r\\n) to a list of Header[]
	 * @param headerString The string to be parsed
	 * @return The Header[] list
	 */
	Header[] parseHeaders(String headerString) {
		String real = headerString.replaceAll("\r\n", ", ");
		HeaderValueParser hvp = new BasicHeaderValueParser();
		HeaderElement[] headerElements = BasicHeaderValueParser.parseElements(real, hvp);
		Header[] headers = new Header[headerElements.length];
		for (int i = 0 ; i < headerElements.length ; i++)
			headers[i] = new BasicHeader(headerElements[i].getName(), headerElements[i].getValue());
		return headers;
	}

	/**
	 * Returns whether or not a response from a cache validation request is valid or not  
	 * @param cacheResponse The response to the cache validation request
	 * @return True - the cache is valid, false - otherwise.
	 */
	boolean isValid(HttpResponse cacheResponse) {
		return (cacheResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED);
	}

	/**
	 * Adds to the cache the response to the request (if allowed by preconditions)
	 * @param request The request received from the client
	 * @param response The response to be delivered back to the client
	 * @throws DbException Thrown in case of a database error
	 */
	void addToCache(HttpRequest request, HttpResponse response) throws DbException{
		try {
			if (checkRequest(request) && checkResponse(response))
				db.addToDb(request.getRequestLine().getUri(), 
							getHeaders(response), 
							EntityUtils.toString(response.getEntity()));
		} catch (ParseException e) {
			HttpProxy.printErr("Couldnt parse entity");
			throw new DbException();
		} catch (IOException e) {
			HttpProxy.printErr("IO problem when retieving entity");
			throw new DbException();
		}
		
		
	}

	/**
	 * Returns a string representation of all the headers of an HttpMessage
	 * @param msg The message
	 * @return The string representation of the headers
	 */
	String getHeaders(HttpMessage msg) {
		Header[] headers = msg.getAllHeaders();
		HeaderElement[] headerElements = new HeaderElement[headers.length];
		for (int i=0 ; i < headers.length ; i++)
			headerElements[i] = new BasicHeaderElement(headers[i].getName(), headers[i].getValue());
		HeaderValueFormatter hvf = new BasicHeaderValueFormatter();
		String s = BasicHeaderValueFormatter.formatElements(headerElements, false , hvf);
		String res = s.replaceAll(", ", "\r\n");
		return res;
	}

	/**
	 * Checks all the preconditions before caching the response
	 * @param response the response we want to cache
	 * @return True - response is cacheable, false - otherwise
	 */
	boolean checkResponse(HttpResponse response) {
		if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			return false;
		if (checkNoCache(response.getHeaders("Cache-Control")))
			return false;
		if (response.getFirstHeader("Last-Modified") == null)
			return false;
		if (response.getFirstHeader("Transfer-Encoding") != null)
			return false;
		return true;
	}

	/**
	 * Checks if the Cache-Control value in headers is either no-cache or no-store
	 * @param headers The headers to be checked
	 * @return True - contains no-cache or no-store, false - otherwise
	 */
	boolean checkNoCache(Header[] headers) {
		for (Header h : headers)
			if (h.getValue().equals("no-cache") || h.getValue().equals("no-store"))
				return true;
		return false;
	}

	/**
	 * Checks all preconditions on the request before caching its reponse
	 * @param request The request
	 * @return True - if request allows cache (GET method), false - otherwise
	 */
	boolean checkRequest(HttpRequest request) {
		return request.getRequestLine().getMethod().equals("GET");
	}

	/**
	 * Returns whether or not the cache db contains an entry for the given request
	 * @param request The request to be searched for.
	 * @return True - the request is in the db, false - otherwise.
	 * @throws DbException
	 */
	boolean inCache(HttpRequest request) throws DbException {
		return db.contains(request.getRequestLine().getUri());
	}

	/**
	 * Sends a request to the remote server, and sets the response accordingly
	 * @param request The request to be sent.
	 * @param response The response to the request 
	 * @throws Internal500Exception Thrown in case of a fatal error causing an 500 response to be sent back.
	 */
	void sendRequest(HttpRequest request, HttpResponse response) throws Internal500Exception{
		DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
		HttpResponse res = null;
		Socket socket = null;
		boolean success = true;
		try {
			HttpProxy.printMsg("\t\tSending: " + request);
			HttpHost host = new HttpHost(request.getFirstHeader("Host").getValue(), 80);//TODO : port
			socket = sockFactory.createSocket(host.getHostName(),host.getPort());
			
			conn.bind(socket, new SyncBasicHttpParams());
			
			HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
			HttpContext outContext = new BasicHttpContext(null);
			HttpProcessor processor = new BasicHttpProcessor();
			
			httpexecutor.preProcess(request, processor, outContext);
			res = httpexecutor.execute(request, conn, outContext);
			httpexecutor.postProcess(res, processor, outContext);
			
			setResponse(res, response);
			HttpProxy.printMsg("\t\tResponse: " + res);
		} catch (UnknownHostException e) {
			HttpProxy.printErr("Couldn't forward message. Unknown host.");
			success = false;
		} catch (IOException e) {
			HttpProxy.printErr("Couldn't forward message. IO malfunction.");
			success = false;
		} catch (HttpException e) {
			HttpProxy.printErr("Couldn't forward message. Http malfunction.");
			success = false;
		} catch (Exception e) {
			HttpProxy.printErr("Couldn't forward message. Unknown problem.");
			e.printStackTrace();
			success = false;
		} finally {
			HttpProxy.close(conn,socket);				
		}
		if (!success)
			throw new Internal500Exception();
	}

}
