package com.agentengine.connectors.http.beans;

import com.agentengine.connectors.infra.beans.ConnectorSpec;

import java.util.Map;

public class HttpConnectorSpec extends ConnectorSpec {
    private String baseUrl;
    private String path;
    private Map<String, String> queryParams;
    private Map<String, String> headers;
    private String method;
    private Map<String, Object> body;

    public HttpConnectorSpec() {
        super(Type.HTTP);
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
