package com.performetriks.performator.distribute;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.distribute.ZePFRServer.Command;

/**********************************************************************************
 * Only use a remote request once.
 *  
 **********************************************************************************/
public class RemoteRequest{
	
	private static final Logger logger = LoggerFactory.getLogger(RemoteRequest.class);
	
	private final ZePFRClient client;
	//------------------------------------
	// Data Fields
	private Command command = null;

	private String testname = null;
	
	private JsonObject parameters = new JsonObject();
	private byte[] body = null;
	
	// doing it like this because the integration with the framework is just shit.
	MonitoredBodyPublisher monitoredPublisher = null;

	//------------------------------------
	// HTTP Client 
	private HttpClient httpClient = null;
	
	
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteRequest(ZePFRClient client, Command command, PFRTest test) {
		this.client = client;
		this.command = command;
		if(test != null) {
			this.testname = test.getName();
		}
		
	}
	
	/********************************************************
	 * Adds a parameter to the request.
	 * 
	 ********************************************************/
	public RemoteRequest testname(String testname) {
		this.testname = testname;
		return this;
	}
	/********************************************************
	 * Adds a parameter to the request.
	 * 
	 ********************************************************/
	public RemoteRequest param(String key, String value) {
		parameters.addProperty(key, value);
		return this;
	}
	
	/********************************************************
	 * Sets and overrides the parameters for the request.
	 * 
	 ********************************************************/
	public RemoteRequest params(JsonObject parameters) {
		this.parameters = parameters.deepCopy();
		return this;
	}
	
	/********************************************************
	 * Sets the body for the request.
	 * 
	 ********************************************************/
	public RemoteRequest body(byte[] body) {
		this.body = body;
		return this;
	}
	
	
	/********************************************************
	 * 
	 * @return HttpClient or initializes it if not done yet.
	 ********************************************************/
	public HttpClient getHttpClient() {
		

		if(httpClient == null) {
			
			// MUST be set BEFORE builder/build
	        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
	        
			//-------------------------
			// Create Builder
			Builder builder = HttpClient.newBuilder()
					.connectTimeout(java.time.Duration.ofSeconds(5))
					;
			
			//-------------------------
			// Create Context
			try {
				SSLContext sslTrustAllContext = SSLContext.getInstance("TLS");
				
				sslTrustAllContext.init(null, new TrustManager[]{
					    new X509TrustManager() {
					        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
					        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
					        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
					    }
					}, new SecureRandom());
				
				builder.sslContext(sslTrustAllContext)
					.sslParameters(new SSLParameters() {{
			            setEndpointIdentificationAlgorithm(""); // disables hostname verification
			        }});
				
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (KeyManagementException e) {
				e.printStackTrace();
			}
			
			//-------------------------
			// Create Client
			httpClient = builder.build();

		}
		
		return httpClient;
		
	}
	
	/********************************************************
	 * Send a request and return a response.
	 * @return response or null on error
	 ********************************************************/
	public void sendAsync(CountDownLatch latch) {
	
		Thread senderThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					try {

						HttpRequest.Builder requestBuilder = prepareRequestBuilder(Duration.ofSeconds(60));
						
						CompletableFuture<HttpResponse<Void>> future = 
								getHttpClient().sendAsync(requestBuilder.build(),
										HttpResponse.BodyHandlers.discarding());
						
						while(!future.isDone()) {
							
							if(monitoredPublisher != null) {
								client.getAgent().uploadProgressPercent(
										monitoredPublisher.getSentPercentage()
								);
							}
							Thread.sleep(100);
						}
						
					} catch (Exception e) {
						logger.error("Error on remote request.", e);
					}
				}finally {
					latch.countDown();
				}
			}
		});
	
		senderThread.start();
	}
	

	/********************************************************
	 * Send a request and return a response.
	 * @param requestTimeout TODO
	 ********************************************************/
	public RemoteResponse send(Duration requestTimeout) {
		
		try {

			HttpRequest.Builder requestBuilder = prepareRequestBuilder(requestTimeout);

			HttpResponse<String> response =
					getHttpClient().send(requestBuilder.build(),
							HttpResponse.BodyHandlers.ofString());
			//client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
			
			return new RemoteResponse(response.body());

		} catch (Exception e) {
			logger.error("Error on remote request.", e);
			return null;
		}
	}

	/********************************************************
	 * Prepare the request
	 ********************************************************/
	private HttpRequest.Builder prepareRequestBuilder(Duration timeout) {
		if(parameters == null) {
			parameters = new JsonObject();
		}

		if(testname != null) {
			parameters.addProperty(ZePFRClient.PARAM_TESTNAME, testname);
		}

		parameters.addProperty(ZePFRClient.PARAM_HOST, ZePFRServer.getLocalhost());
		//parameters.addProperty(ZePFRClient.PARAM_PORT, PFRConfig.port());

		String query = buildQuery(parameters);

		String url = "https://" + client.getHost() + ":" + client.getPort()
						+ "/api?command=" + command.name() + "&" + query;

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(timeout);


		if(body != null) {
			monitoredPublisher = new MonitoredBodyPublisher(body);
			requestBuilder.POST(monitoredPublisher);
		} else {
			requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
		}
		return requestBuilder;
	}

	/********************************************************
	 * Send a request and return a response.
	 ********************************************************/
	private String buildQuery(JsonObject json) {
		return json.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
						+ "="
						+ URLEncoder.encode(e.getValue().getAsString(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}
	
	/********************************************************
	 * Send a request and return a response.
	 ********************************************************/
	public class MonitoredBodyPublisher implements BodyPublisher {

	    private BodyPublisher delegate;
	    private AtomicLong bytesSent = new AtomicLong();

	    public MonitoredBodyPublisher(byte[] bodyBytes) {
	        this.delegate = HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
	    }

	    public long getBytesSent() {
	        return bytesSent.get();
	    }

	    @Override
	    public long contentLength() {
	        return delegate.contentLength();
	    }
	    
	    public int getSentPercentage() {
	    	
	    	if(delegate.contentLength() <= 0) {  return 100; }
	    	
	    	int percent = (int) ((bytesSent.get() * 100) / delegate.contentLength());
	        return percent;
	    }

	    @Override
	    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
	        delegate.subscribe(new Flow.Subscriber<>() {
	            @Override
	            public void onSubscribe(Flow.Subscription subscription) {
	                subscriber.onSubscribe(subscription);
	            }

	            @Override
	            public void onNext(ByteBuffer item) {
	                bytesSent.addAndGet(item.remaining());
	                subscriber.onNext(item);
	            }

	            @Override
	            public void onError(Throwable throwable) {
	                subscriber.onError(throwable);
	            }

	            @Override
	            public void onComplete() {
	                subscriber.onComplete();
	            }
	        });
	    }
	}
}