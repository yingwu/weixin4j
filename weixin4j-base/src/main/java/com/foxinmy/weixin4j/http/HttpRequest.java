package com.foxinmy.weixin4j.http;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSONException;
import com.foxinmy.weixin4j.exception.WeixinException;

/**
 * 调用微信相关接口的HttpRequest,对于其他请求可能并不试用
 * 
 * @className HttpRequest
 * @author jy
 * @date 2014年8月21日
 * @since JDK 1.7
 * @see
 */
public class HttpRequest {
	private final String SUCCESS = "success";
	private AbstractHttpClient client;

	public HttpRequest() {
		this(150, 100, 10000, 10000);
	}

	public HttpRequest(int maxConPerRoute, int maxTotal, int socketTimeout,
			int connectionTimeout) {
		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
		// 指定IP并发最大数
		connectionManager.setDefaultMaxPerRoute(maxConPerRoute);
		// socket最大创建数
		connectionManager.setMaxTotal(maxTotal);

		client = new DefaultHttpClient(connectionManager);
		client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT,
				socketTimeout);
		client.getParams().setParameter(
				CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeout);
		client.getParams().setBooleanParameter(
				CoreConnectionPNames.TCP_NODELAY, false);
		client.getParams().setParameter(
				CoreConnectionPNames.SOCKET_BUFFER_SIZE, 1024 * 1024);
		client.getParams().setParameter(ClientPNames.COOKIE_POLICY,
				CookiePolicy.IGNORE_COOKIES);
		client.getParams().setParameter(
				CoreProtocolPNames.HTTP_CONTENT_CHARSET, Consts.UTF_8);
		client.getParams().setParameter(HttpHeaders.CONTENT_ENCODING,
				Consts.UTF_8);
		client.getParams().setParameter(HttpHeaders.ACCEPT_CHARSET,
				Consts.UTF_8);
	}

	public Response get(String url) throws WeixinException {
		return get(url, (Parameter[]) null);
	}

	public Response get(String url, Parameter... parameters)
			throws WeixinException {
		StringBuilder sb = new StringBuilder(url);
		if (parameters != null && parameters.length > 0) {
			if (url.indexOf("?") < 0) {
				sb.append(String.format("?%s=%s", parameters[0].getName(),
						parameters[0].getValue()));
			}
			for (int i = 0; i < parameters.length; i++) {
				sb.append(parameters[i].toGetPara());
			}
		}
		return doRequest(new HttpGet(sb.toString()));
	}

	public Response post(String url) throws WeixinException {
		return post(url, (Parameter[]) null);
	}

	public Response post(String url, Parameter... parameters)
			throws WeixinException {
		HttpPost method = new HttpPost(url);
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (Parameter parameter : parameters) {
			params.add(parameter.toPostPara());
		}
		method.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));
		return doRequest(method);
	}

	public Response post(String url, String body) throws WeixinException {
		HttpPost method = new HttpPost(url);
		method.setEntity(new StringEntity(body, ContentType.create(
				ContentType.APPLICATION_JSON.getMimeType(), Consts.UTF_8)));
		return doRequest(method);
	}

	public Response post(String url, byte[] bytes) throws WeixinException {
		HttpPost method = new HttpPost(url);
		method.setEntity(new ByteArrayEntity(bytes, ContentType.create(
				ContentType.MULTIPART_FORM_DATA.getMimeType(), Consts.UTF_8)));
		return doRequest(method);
	}

	public Response post(String url, File file) throws WeixinException {
		HttpPost method = new HttpPost(url);
		method.setEntity(new FileEntity(file, ContentType.create(
				ContentType.APPLICATION_OCTET_STREAM.getMimeType(),
				Consts.UTF_8)));
		return doRequest(method);
	}

	public Response post(String url, PartParameter... paramters)
			throws WeixinException {
		HttpPost method = new HttpPost(url);
		MultipartEntity entity = new MultipartEntity();
		for (PartParameter paramter : paramters) {
			entity.addPart(paramter.getName(), paramter.getContentBody());
		}
		method.setEntity(entity);

		return doRequest(method);
	}

	protected Response doRequest(HttpRequestBase request)
			throws WeixinException {
		Response response = null;
		try {
			HttpResponse httpResponse = client.execute(request);
			StatusLine statusLine = httpResponse.getStatusLine();
			HttpEntity httpEntity = httpResponse.getEntity();

			int status = statusLine.getStatusCode();
			if (status != HttpStatus.SC_OK) {
				throw new WeixinException(status + "", "request fail");
			}
			// 301或者302
			if (status == HttpStatus.SC_MOVED_PERMANENTLY
					|| status == HttpStatus.SC_MOVED_TEMPORARILY) {
				throw new WeixinException(status + "", String.format(
						"the page was redirected to %s",
						httpResponse.getFirstHeader("location")));
			}
			byte[] data = EntityUtils.toByteArray(httpEntity);
			response = new Response();
			response.setBody(data);
			response.setStatusCode(status);
			response.setStatusText(statusLine.getReasonPhrase());
			response.setStream(new ByteArrayInputStream(data));

			EntityUtils.consume(httpEntity);
			Header contentType = httpResponse
					.getFirstHeader(HttpHeaders.CONTENT_TYPE);
			if (contentType.getValue().contains(
					ContentType.APPLICATION_JSON.getMimeType())
					|| contentType.getValue().contains(
							ContentType.TEXT_PLAIN.getMimeType())) {
				response.setText(new String(data, StandardCharsets.UTF_8));
				try {
					JsonResult jsonResult = response.getAsJsonResult();
					response.setJsonResult(true);
					if (jsonResult.getCode() != 0) {
						throw new WeixinException(jsonResult.getCode() + "",
								jsonResult.getDesc());
					}
					return response;
				} catch (JSONException e) {
					;
				}
				XmlResult xmlResult = response.getAsXmlResult();
				response.setXmlResult(true);
				if (!xmlResult.getReturnCode().equalsIgnoreCase(SUCCESS)) {
					throw new WeixinException(xmlResult.getReturnCode(),
							xmlResult.getReturnMsg());
				}
				if (!xmlResult.getResultCode().equalsIgnoreCase(SUCCESS)) {
					throw new WeixinException(xmlResult.getErrCode(),
							xmlResult.getErrCodeDes());
				}
			}
		} catch (IOException e) {
			throw new WeixinException(e.getMessage());
		} finally {
			request.releaseConnection();
		}
		return response;
	}
}
