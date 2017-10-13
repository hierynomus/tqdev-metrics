/* Copyright (C) 2017 Maurits van der Schee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.tqdev.metrics.jetty;

import com.tqdev.metrics.core.MetricRegistry;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.AsyncContextState;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * An instrumented Jetty handler wrapper to keep track of total duration and
 * invocation count of requests grouped by status code, method and path.
 */
public class InstrumentedHandler extends HandlerWrapper {

	/** The registry to store metrics in. */
	private final MetricRegistry registry;

	/**
	 * The content types for which the path is grouped, e.g: "json|xml|html|csv"
	 */
	private final String contentTypes;

	/**
	 * Instantiates a new instrumented handler.
	 *
	 * @param registry
	 *            the registry
	 */
	public InstrumentedHandler(String contentTypes, MetricRegistry registry) {
		this.contentTypes = contentTypes;
		this.registry = registry;
	}

	/** The listener to handle async requests consistently. */
	private AsyncListener listener = new AsyncListener() {
		private long startTime;

		@Override
		public void onTimeout(AsyncEvent event) throws IOException {
			registry.increment("jetty.Other.Counters", "async-timeouts");
		}

		@Override
		public void onStartAsync(AsyncEvent event) throws IOException {
			startTime = System.currentTimeMillis();
			event.getAsyncContext().addListener(this);
		}

		@Override
		public void onError(AsyncEvent event) throws IOException {
			registry.increment("jetty.Other.Counters", "async-errors");
		}

		@Override
		public void onComplete(AsyncEvent event) throws IOException {
			final AsyncContextState state = (AsyncContextState) event.getAsyncContext();
			final HttpServletRequest request = (HttpServletRequest) state.getRequest();
			final HttpServletResponse response = (HttpServletResponse) state.getResponse();
			updateResponses(request, response, startTime);
			if (state.getHttpChannelState().getState() != HttpChannelState.State.DISPATCHED) {
				registry.decrement("jetty.Other.Gauges", "active-suspended");
			}
		}
	};

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jetty.server.handler.AbstractHandler#doStart()
	 */
	@Override
	protected void doStart() throws Exception {
		super.doStart();

		registry.set("jetty.Other.Counters", "async-timeouts", 0);
		registry.set("jetty.Other.Counters", "async-errors", 0);
		registry.set("jetty.Other.Gauges", "active-suspended", 0);
		registry.set("jetty.Other.Gauges", "active-dispatches", 0);
		registry.set("jetty.Other.Gauges", "active-requests", 0);
		registry.set("jetty.Other.Gauges", "async-dispatches", 0);
		for (int responseStatus = 1; responseStatus <= 5; responseStatus++) {
			registry.set("jetty.Response.Invocations", responseStatus + "xx-responses", 0);
			registry.set("jetty.Response.Durations", responseStatus + "xx-responses", 0);
		}
		registry.set("jetty.Response.Invocations", "other-responses", 0);
		registry.set("jetty.Response.Durations", "other-responses", 0);
		for (HttpMethod method : HttpMethod.values()) {
			registry.set("jetty.Request.Invocations", method.asString().toLowerCase() + "-requests", 0);
			registry.set("jetty.Request.Durations", method.asString().toLowerCase() + "-requests", 0);
		}
		registry.set("jetty.Request.Invocations", "other-requests", 0);
		registry.set("jetty.Request.Durations", "other-requests", 0);
		registry.set("jetty.Aggregated.Invocations", "requests", 0);
		registry.set("jetty.Aggregated.Durations", "requests", 0);
		registry.set("jetty.Aggregated.Invocations", "dispatches", 0);
		registry.set("jetty.Aggregated.Durations", "dispatches", 0);

		registry.set("jetty.Thread.Gauges", "threads", () -> getServer().getThreadPool().getThreads());
		registry.set("jetty.Thread.Gauges", "idle-threads", () -> getServer().getThreadPool().getIdleThreads());
		if (getServer().getThreadPool() instanceof QueuedThreadPool) {
			registry.set("jetty.Thread.Gauges", "busy-threads",
					() -> ((QueuedThreadPool) getServer().getThreadPool()).getBusyThreads());
			registry.set("jetty.Thread.Gauges", "min-threads",
					() -> ((QueuedThreadPool) getServer().getThreadPool()).getMinThreads());
			registry.set("jetty.Thread.Gauges", "max-threads",
					() -> ((QueuedThreadPool) getServer().getThreadPool()).getMaxThreads());
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.jetty.server.handler.HandlerWrapper#handle(java.lang.String,
	 * org.eclipse.jetty.server.Request, javax.servlet.http.HttpServletRequest,
	 * javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void handle(String path, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
			throws IOException, ServletException {

		registry.increment("jetty.Other.Gauges", "active-dispatches");

		final long start;
		final HttpChannelState state = request.getHttpChannelState();
		if (state.isInitial()) {
			// new request
			registry.increment("jetty.Other.Gauges", "active-requests");
			start = request.getTimeStamp();
			state.addListener(listener);
		} else {
			// resumed request
			start = System.currentTimeMillis();
			registry.decrement("jetty.Other.Gauges", "active-suspended");
			if (state.getState() == HttpChannelState.State.DISPATCHED) {
				registry.increment("jetty.Other.Gauges", "async-dispatches");
			}
		}

		try {
			super.handle(path, request, httpRequest, httpResponse);
		} finally {
			final long duration = System.currentTimeMillis() - start;

			registry.decrement("jetty.Other.Gauges", "active-dispatches");
			registry.increment("jetty.Aggregated.Invocations", "dispatches");
			registry.add("jetty.Aggregated.Durations", "dispatches", duration);

			if (state.isSuspended()) {
				registry.increment("jetty.Other.Gauges", "active-suspended");
			} else if (state.isInitial()) {
				updateResponses(httpRequest, httpResponse, start);
			}
			// else onCompletion will handle it.
		}
	}

	/**
	 * Get a grouping identifier for metrics based on request method.
	 *
	 * @param method
	 *            the method
	 * @return the string
	 */
	private String getMethodGroup(String method) {
		final HttpMethod m = HttpMethod.fromString(method);
		if (m == null) {
			return "other";
		}
		return m.asString().toLowerCase();
	}

	/**
	 * Get a grouping identifier for metrics based on response status.
	 *
	 * @param status
	 *            the status
	 * @return the string
	 */
	private String getStatusGroup(int status) {
		final int responseStatus = status / 100;
		if ((responseStatus < 1) || (responseStatus > 5)) {
			return "other";
		}
		return responseStatus + "xx";
	}

	/**
	 * Get a grouping identifier for metrics based on path.
	 *
	 * @param requestURI
	 *            the request URI
	 * @param contentType
	 *            the content type
	 * @return the string
	 */
	private String getPathGroup(String requestURI, String contentType) {
		if (!contentType.matches(".*" + contentTypes + ".*")) {
			return "(other)";
		}
		String parts[] = requestURI.split("/");
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
				parts[i] = "(uuid)";
			} else if (parts[i].matches("[a-f0-9]{128}")) {
				parts[i] = "(sha512)";
			} else if (parts[i].matches("[a-f0-9]{64}")) {
				parts[i] = "(sha256)";
			} else if (parts[i].matches("[a-f0-9]{40}")) {
				parts[i] = "(sha1)";
			} else if (parts[i].matches("[a-f0-9]{32}")) {
				parts[i] = "(md5)";
			} else if (parts[i].matches("[^a-zA-Z]+") && parts[i].matches(".*[0-9].*")) {
				parts[i] = "(number)";
			}
		}
		String path = String.join("/", parts);
		if ("".equals(path)) {
			path = "/";
		}
		return path;
	}

	/**
	 * Update response based metrics such as duration.
	 *
	 * @param request
	 *            the request
	 * @param response
	 *            the response
	 * @param start
	 *            the start
	 */
	private void updateResponses(HttpServletRequest request, HttpServletResponse response, long start) {
		registry.decrement("jetty.Other.Gauges", "active-requests");
		final long duration = System.currentTimeMillis() - start;
		registry.increment("jetty.Aggregated.Invocations", "requests");
		registry.add("jetty.Aggregated.Durations", "requests", duration);
		final String methodGroup = getMethodGroup(request.getMethod());
		registry.increment("jetty.Request.Invocations", methodGroup + "-requests");
		registry.add("jetty.Request.Durations", methodGroup + "-requests", duration);
		final String statusGroup = getStatusGroup(response.getStatus());
		registry.increment("jetty.Response.Invocations", statusGroup + "-responses");
		registry.add("jetty.Response.Durations", statusGroup + "-responses", duration);
		if (contentTypes != null) {
			final String pathGroup = getPathGroup(request.getRequestURI(), response.getContentType());
			registry.increment("jetty.Path.Invocations", pathGroup);
			registry.add("jetty.Path.Durations", pathGroup, duration);
		}
	}
}
