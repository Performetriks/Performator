package com.performetriks.performator.distribute;

import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRConfig;
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
	private PFRTest test = null;
	private JsonObject parameters = new JsonObject();
	private byte[] body = null;
	
	// doing it like this because the integration with the framework is just shit.
	MonitoredBodyPublisher monitoredPublisher = null;
	//------------------------------------
	// HTTP Client 
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(30))
			.build();
	
	
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteRequest(ZePFRClient client, Command command, PFRTest test) {
		this.client = client;
		this.command = command;
		this.test = test;
		
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
	 * Send a request and return a response.
	 * @return response or null on error
	 ********************************************************/
	public void sendAsync(CountDownLatch latch) {
	
		Thread senderThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					try {

						HttpRequest.Builder requestBuilder = prepareRequestBuilder();
						
						CompletableFuture<HttpResponse<Void>> future = 
								httpClient.sendAsync(requestBuilder.build(),
										HttpResponse.BodyHandlers.discarding());
						
						while(!future.isDone()) {
							
							if(monitoredPublisher != null) {
								client.getAgent().uploadProgressPercent(
										monitoredPublisher.getSentPercentage()
								);
							}
							Thread.sleep(10);
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
	 ********************************************************/
	public RemoteResponse send() {
		
		try {

			HttpRequest.Builder requestBuilder = prepareRequestBuilder();

			HttpResponse<String> response =
					httpClient.send(requestBuilder.build(),
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
	private HttpRequest.Builder prepareRequestBuilder() throws UnknownHostException {
		if(parameters == null) {
			parameters = new JsonObject();
		}

		if(test != null) {
			parameters.addProperty(ZePFRClient.PARAM_TEST, test.getName());
		}

		parameters.addProperty(ZePFRClient.PARAM_HOST, ZePFRServer.getLocalhost());
		parameters.addProperty(ZePFRClient.PARAM_PORT, PFRConfig.port());

		String query = buildQuery(parameters);

		String url = "http://" + client.getHost() + ":" + client.getPort()
						+ "/api?command=" + command.name() + "&" + query;

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(java.time.Duration.ofMinutes(3));


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