package com.kolich.curacao.examples.controllers;

import static com.google.common.base.Charsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;

import com.kolich.curacao.annotations.Controller;
import com.kolich.curacao.annotations.Injectable;
import com.kolich.curacao.annotations.methods.GET;
import com.kolich.curacao.examples.components.AsyncHttpClientComponent;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;

@Controller
public final class WebServiceExampleController {
	
	private static final Logger logger__ =
		getLogger(WebServiceExampleController.class);
	
	private final AsyncHttpClientComponent client_;
	
	@Injectable
	public WebServiceExampleController(final AsyncHttpClientComponent client) {
		client_ = client;
	}
	
	@GET("/api/webservice")
	public final Future<String> callWebServiceAsync() throws IOException {
		// Use the Ning AsyncHttpClient to make a call to an external web
		// service and immediately return a Future<?> that will "complete"
		// when the AsyncHttpClient has fetched the page.
		return client_.getClient().prepareGet("http://www.google.com/robots.txt")
			.execute(new AsyncCompletionHandler<String>() {
				@Override
				public String onCompleted(final Response response) throws Exception {
					return response.getResponseBody(UTF_8.toString());
				}
				@Override
				public void onThrowable(Throwable t) {
					logger__.error("Web-service request failed " +
						"miserably.", t);
				}
			});
	}

}
