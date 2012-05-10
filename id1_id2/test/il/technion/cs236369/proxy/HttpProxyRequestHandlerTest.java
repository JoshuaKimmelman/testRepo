package il.technion.cs236369.proxy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

public class HttpProxyRequestHandlerTest {
	HttpRequest req;
	HttpRequest req2;
	HttpResponse res;
	HttpProxyDatabaseStub db = new HttpProxyDatabaseStub("", "", "", "", "", "");
	HttpProxyRequestHandler pr = new HttpProxyRequestHandler(SocketFactory.getDefault() , db);
	String reqUrl;
	
	@Before
	public void setUp() {
		req = new BasicHttpRequest("GET", "/Protocols/rfc2616/rfc2616-sec10.html");
		req.addHeader("Host", "www.w3.org");
		
		req2 = new BasicHttpRequest("GET", "/Protocols/rfc2616/rfc2616-sec11.html");
		req2.addHeader("Host", "www.w3.org");
		reqUrl = req.getRequestLine().getUri();
	}
	

	@Test
	public void testGetModifiedSince() throws DbException, BadDbEntry, UnsupportedEncodingException {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		r1.addHeader("a","1");
		r1.addHeader("Last-Modified","1");
		r1.setEntity(new StringEntity("abc"));
		pr.addToCache(req, r1);
		assertTrue(db.contains(reqUrl));
		assertEquals("1", pr.getModifiedSince(req));
		pr.removeFromCache(req);
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testSetResponse() throws ParseException, IOException, Internal500Exception {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		r1.addHeader("a","1");
		r1.addHeader("Last-Modified","1");
		r1.setEntity(new StringEntity("abc"));
		HttpResponse r2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND,"adsf");
		pr.setResponse(r1, r2);
		assertEquals(HttpStatus.SC_OK, r2.getStatusLine().getStatusCode());
		assertEquals("", r2.getStatusLine().getReasonPhrase());
		assertEquals("abc", EntityUtils.toString(r2.getEntity()));
		assertNotNull(r2.getFirstHeader("a"));
		assertEquals("1", r2.getFirstHeader("a").getValue());
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testRemoveFromCache() throws UnsupportedEncodingException, DbException {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		r1.addHeader("a","1");
		r1.addHeader("Last-Modified","1");
		r1.setEntity(new StringEntity("abc"));
		pr.addToCache(req, r1);
		assertTrue(db.contains(reqUrl));
		pr.removeFromCache(req);
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testGetResponseFromCache() throws DbException, BadDbEntry, ParseException, IOException {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		r1.addHeader("a","1");
		r1.addHeader("Last-Modified","1");
		r1.setEntity(new StringEntity("abc"));
		pr.addToCache(req, r1);
		assertTrue(db.contains(reqUrl));
		HttpResponse r2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND,"adsf");
		pr.getResponseFromCache(req, r2);
		assertEquals(HttpStatus.SC_OK, r2.getStatusLine().getStatusCode());
		assertEquals(null, r2.getStatusLine().getReasonPhrase());
		assertEquals("abc", EntityUtils.toString(r2.getEntity()));
		assertNotNull(r2.getFirstHeader("a"));
		assertEquals("1", r2.getFirstHeader("a").getValue());
		db.removeFromDb(reqUrl);
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testParseHearders() {
		Header[] h = pr.parseHearders("a=1\r\nb=2");
		assertEquals("a", h[0].getName());
		assertEquals("1", h[0].getValue());
		assertEquals("b", h[1].getName());
		assertEquals("2", h[1].getValue());
		assertTrue(db.db.isEmpty());
		
	}

	@Test
	public void testIsValid() {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		HttpResponse r2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_MODIFIED,"");
		assertTrue(pr.isValid(r2));
		assertFalse(pr.isValid(r1));
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testAddToCache() throws UnsupportedEncodingException, DbException {
		HttpResponse res = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		res.setEntity(new StringEntity("a"));
		res.addHeader("a","1");
		res.addHeader("Last-Modified","2");
		pr.addToCache(req, res);
		assertTrue(db.contains(reqUrl));
		assertEquals("a",db.getBody(reqUrl));
		assertEquals("a=1\r\nLast-Modified=2",db.getHeaders(reqUrl));
		db.removeFromDb(reqUrl);
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testGetHeaders() {
		HttpRequest r1 = new BasicHttpRequest(req.getRequestLine());
		r1.addHeader("a", "1");
		r1.addHeader("b", "2");
		String h = pr.getHeaders(r1);
		assertEquals("a=1\r\nb=2",h);
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testCheckResponse() {
		HttpResponse r1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND,"");
		HttpResponse r2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		HttpResponse r3 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		HttpResponse r4 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		HttpResponse r5 = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK,"");
		r1.addHeader("Last-Modified","1");
		r2.addHeader("Last-Modified","1");
		r2.addHeader("Cache-Control","no-cache");
		r3.addHeader("Last-Modified","1");
		r3.addHeader("Cache-Control","no-store");
		r5.addHeader("Last-Modified","1");
		assertFalse(pr.checkResponse(r1));
		assertFalse(pr.checkResponse(r2));
		assertFalse(pr.checkResponse(r3));
		assertFalse(pr.checkResponse(r4));
		assertTrue(pr.checkResponse(r5));
		assertTrue(db.db.isEmpty());
		
	}

	@Test
	public void testCheckNoCache() {
		HttpRequest r1 = new BasicHttpRequest(req.getRequestLine());
		HttpRequest r2 = new BasicHttpRequest(req.getRequestLine());
		HttpRequest r3 = new BasicHttpRequest(req.getRequestLine());
		HttpRequest r4 = new BasicHttpRequest(req.getRequestLine());
		r1.addHeader("Cache-Control", "no-cache");
		r2.addHeader("Cache-Control", "no-cache");
		r3.addHeader("Cache-Control", "no1-cache");
		assertTrue(pr.checkNoCache(r1.getAllHeaders()));
		assertTrue(pr.checkNoCache(r2.getAllHeaders()));
		assertFalse(pr.checkNoCache(r3.getAllHeaders()));
		assertFalse(pr.checkNoCache(r4.getAllHeaders()));
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testCheckRequest() {
		HttpRequest r1 = new BasicHttpRequest(req.getRequestLine());
		HttpRequest r2 = new BasicHttpRequest("POST", reqUrl);
		assertTrue(pr.checkRequest(r1));
		assertFalse(pr.checkRequest(r2));
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testInCache() throws DbException {
		db.addToDb(reqUrl,"","");
		assertTrue(pr.inCache(req));
		db.removeFromDb(reqUrl);
		assertFalse(pr.inCache(req));
		assertTrue(db.db.isEmpty());
	}

	@Test
	public void testSendRequest() throws Internal500Exception {
		HttpResponse res = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND,"");
		pr.sendRequest(req, res);
		assertEquals(HttpStatus.SC_OK, res.getStatusLine().getStatusCode());
		assertTrue(db.db.isEmpty());
	}

}
