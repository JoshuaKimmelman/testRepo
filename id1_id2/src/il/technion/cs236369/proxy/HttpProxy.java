package il.technion.cs236369.proxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;

public class HttpProxy {

	private SocketFactory sockFactory;
	private ServerSocketFactory srvSockFactory;
	private int port;
	private ServerSocket srvSock;
	private SyncBasicHttpParams params;
	private HttpService httpService;
	private IHttpProxyDatabase db;


	/**
	 * Constructs the proxy
	 * 
	 * @param sockFactory The SocketFactory to be used for creating new sockets for connecting
	 * to servers
	 * 
	 * @param srvSockFactory The ServerSocketFactory to be used for creating a single ServerSocket for
	 * listening for clients requests
	 * 
	 * @param port The port number to bounded by the ServerSocket
	 * 
	 * @param dbURL url of the database (e.g. jdbc:mysql://127.0.0.1:3306/)
	 * @param dbName The name of the database (e.g. proxy)
	 * @param tblName The name of the table in the database (e.g. cache)
	 * @param dbUsername Database's username (e.g. root)
	 * @param dbPassword Database's password
	 * @param dbDriver Database's driver class name (com.mysql.jdbc.Driver)
	 */
	
	@Inject
	HttpProxy(
			SocketFactory sockFactory,
			ServerSocketFactory srvSockFactory,
			@Named("httproxy.net.port") int port,
			@Named("httproxy.db.url") String dbURL,
			@Named("httproxy.db.name") String dbName,
			@Named("httproxy.db.table") String tblName,
			@Named("httproxy.db.username") String dbUsername,
			@Named("httproxy.db.password") String dbPassword,
			@Named("httproxy.db.driver") String dbDriver) {
		
		// YOUR IMPLEMENTATION HERE
		db = new HttpProxyDatabaseStub(dbURL, dbName, tblName, dbUsername, dbPassword, dbDriver);
		
		this.sockFactory = sockFactory;
		this.srvSockFactory = srvSockFactory;
		this.port = port;
        this.params = new SyncBasicHttpParams();

        // Set up the HTTP protocol processor
        HttpProcessor httpproc = new BasicHttpProcessor();

        // Set up request handlers
        HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
        reqistry.register("*", new HttpProxyRequestHandler(this.sockFactory, db));

        // Set up the HTTP service
        this.httpService = new HttpService(
                httpproc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(),
                reqistry,
                this.params);
        //Now we start the db :)
        
	}
	
	
	/**
	 * Create a new bounded server socket using ServerSocketFactory with the given port
	 * @throws IOException unable to bind the server socket
	 */
	public void bind() throws IOException {
		// YOUR IMPLEMENTATION HERE
		srvSock = srvSockFactory.createServerSocket(port);
	}
	
	
	
	/**
	 * Starts the server loop:
	 * listens to client requests and executes them.
	 * To create new sockets for connecting to servers use ONLY SocketFactory.createSocket(String host, int port)
	 * where SocketFactory is the one passed to the constructor.
	 */
	public void start() {
		// YOUR IMPLEMENTATION HERE
		while (true) {
			Socket reqSock = null;
			DefaultHttpServerConnection conn = new DefaultHttpServerConnection();;
			try {
				printMsg("Waiting for action...");
				reqSock = srvSock.accept();
				conn.bind(reqSock, new BasicHttpParams());
				this.httpService.handleRequest(conn, new BasicHttpContext(null));
			} catch (IOException e) {
				errorPrint("Error accepting request on proxy server. IO malfunction.");
			} catch (HttpException e) {
				errorPrint("Error accepting request on proxy server. Http malfunction.");
			} catch (Exception e) {
				errorPrint("Error accepting request on proxy server. Unknown error.");
			} finally {
				close(conn, reqSock);
			}
		}
	}
	
	
	public static void printMsg(String string) {
		System.out.println(string);
	}


	public static void errorPrint(String string) {
		System.err.println(string);
	}


	public static void close(HttpConnection conn, Socket socket) {
		try {
			if (conn != null)
				conn.close();
			if (socket != null)
				socket.close();
		} catch (Exception ignore) {}
	}


	public static void main(String[] args) throws Exception {
		// first arg is the path to the config file
		Properties props = new Properties();
		props.load(new FileInputStream(args[0]));
		Injector injector = Guice.createInjector(new HttpProxyModule(props));
		HttpProxy proxy = injector.getInstance(HttpProxy.class);
		proxy.bind();
		proxy.start();
		
	}
	
	public static void set500Response(HttpResponse response) {
		response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
	}
}

