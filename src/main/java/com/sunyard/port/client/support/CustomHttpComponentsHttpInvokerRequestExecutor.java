package com.sunyard.port.client.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.httpinvoker.HttpComponentsHttpInvokerRequestExecutor;
import org.springframework.util.StringUtils;

public class CustomHttpComponentsHttpInvokerRequestExecutor extends HttpComponentsHttpInvokerRequestExecutor {
	static org.slf4j.Logger log = LoggerFactory.getLogger(ClassEmitter.class);
	private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 2000;
	private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 100;
	private static final int DEFAULT_READ_TIMEOUT_MILLISECONDS = (60 * 1000);

	public CustomHttpComponentsHttpInvokerRequestExecutor() {
	    super(makeDefaultHttpClient());
	    setReadTimeout(DEFAULT_READ_TIMEOUT_MILLISECONDS);
	}

	public CustomHttpComponentsHttpInvokerRequestExecutor(final int duration, final TimeUnit unit) {
		this(Long.valueOf(unit.toMillis(duration)).intValue());
	}

	public CustomHttpComponentsHttpInvokerRequestExecutor(final int duration) {
		super(makeDefaultHttpClient());
		setReadTimeout(duration);
	}

	private static HttpClient makeDefaultHttpClient() {
	    // New non-deprecated ConnectionManager with same settings as super()
	    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
	    connectionManager.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTIONS);
	    connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE);

	    // HttpClient with ConnectionManager and no retry
	    /*
	     * TODO Add a request interceptor that will authenticate
	     * if credentials with AuthScope.ANY are provided.
	     */
	    HttpClient httpClient = HttpClientBuilder.create().setConnectionManager(connectionManager)
	            .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false)).build();
	    return httpClient;
	}

	public byte[] doPost(final String url, final byte[] data, final String charset) {
		byte[] empty = new byte[0], result = empty;
		if (StringUtils.isEmpty(url))
			return empty;
		try {
			HttpPost httpPost = new HttpPost(url);
			RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(DEFAULT_READ_TIMEOUT_MILLISECONDS).build();
			httpPost.setConfig(config);
			httpPost.setEntity(new ByteArrayEntity(data));
			HttpResponse response = getHttpClient().execute(httpPost);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				httpPost.abort();
				throw new RuntimeException("HttpClient, error status code :" + statusCode);
			}
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				result = EntityUtils.toByteArray(entity);
			}
			EntityUtils.consume(entity);
			return result;
		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		return empty;
	}

	public String doPost(final String url, final Map<String, String> params, final String charset) {
		if (StringUtils.isEmpty(url))
			return "";
		try {
			List<NameValuePair> pairs = null;
			if (params != null && !params.isEmpty()) {
				pairs = new ArrayList<NameValuePair>(params.size());
				for (Map.Entry<String, String> entry : params.entrySet()) {
					String value = entry.getValue();
					if (value != null) {
						pairs.add(new BasicNameValuePair(entry.getKey(), value));
					}
				}
			}
			HttpPost httpPost = new HttpPost(url);
			RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(DEFAULT_READ_TIMEOUT_MILLISECONDS).build();
			httpPost.setConfig(config);
			if (pairs != null && pairs.size() > 0) {
				httpPost.setEntity(new UrlEncodedFormEntity(pairs, charset));
			}
			String result = "";
			HttpResponse response = getHttpClient().execute(httpPost);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				httpPost.abort();
				throw new RuntimeException("HttpClient, error status code :" + statusCode);
			}
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				result = EntityUtils.toString(entity, charset);
			}
			EntityUtils.consume(entity);
			return result;
		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		return "";
	}

}
