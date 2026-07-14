package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * WSDataObjectParamsBean class
 * TODO: Add proper implementation based on actual requirements
 */
public class WSDataObjectParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String paramsAndValues;
    
    public String getParamsAndValues() {
        return paramsAndValues;
    }
    
    public void setParamsAndValues(String paramsAndValues) {
        this.paramsAndValues = paramsAndValues;
    }
    
    private String requestMethod;
    
    public String getRequestMethod() {
        return requestMethod;
    }
    
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }
    
    private String resultFormat;
    
    public String getResultFormat() {
        return resultFormat;
    }
    
    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }
    
    private String encode;
    
    public String getEncode() {
        return encode;
    }
    
    public void setEncode(String encode) {
        this.encode = encode;
    }
    
    private String xpath;
    
    public String getXpath() {
        return xpath;
    }
    
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }
    
    private String encoding;
    
    public String getEncoding() {
        return encoding;
    }
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    private String tokenVector;
    
    public String getTokenVector() {
        return tokenVector;
    }
    
    public void setTokenVector(String tokenVector) {
        this.tokenVector = tokenVector;
    }
    
    private String tokenSource;
    
    public String getTokenSource() {
        return tokenSource;
    }
    
    public void setTokenSource(String tokenSource) {
        this.tokenSource = tokenSource;
    }
    
    private String tokenField;
    
    public String getTokenField() {
        return tokenField;
    }
    
    public void setTokenField(String tokenField) {
        this.tokenField = tokenField;
    }
    
    private String tokenValue;
    
    public String getTokenValue() {
        return tokenValue;
    }
    
    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }
    
    private String authorizationType;
    
    public String getAuthorizationType() {
        return authorizationType;
    }
    
    public void setAuthorizationType(String authorizationType) {
        this.authorizationType = authorizationType;
    }
    
    private String recordSizePath;
    
    public String getRecordSizePath() {
        return recordSizePath;
    }
    
    public void setRecordSizePath(String recordSizePath) {
        this.recordSizePath = recordSizePath;
    }
    
    private String requestEntityType;
    
    public String getRequestEntityType() {
        return requestEntityType;
    }
    
    public void setRequestEntityType(String requestEntityType) {
        this.requestEntityType = requestEntityType;
    }
    
    private String requestParamTemplate;
    
    public String getRequestParamTemplate() {
        return requestParamTemplate;
    }
    
    public void setRequestParamTemplate(String requestParamTemplate) {
        this.requestParamTemplate = requestParamTemplate;
    }
    
    private String requestTimeout;
    
    public String getRequestTimeout() {
        return requestTimeout;
    }
    
    public void setRequestTimeout(String requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
    
    private String resultPath;
    
    public String getResultPath() {
        return resultPath;
    }
    
    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }
    
    private String tokenPath;
    
    public String getTokenPath() {
        return tokenPath;
    }
    
    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }
    
    private String tokenRequestEntity;
    
    public String getTokenRequestEntity() {
        return tokenRequestEntity;
    }
    
    public void setTokenRequestEntity(String tokenRequestEntity) {
        this.tokenRequestEntity = tokenRequestEntity;
    }
    
    private String tokenRequestHeader;
    
    public String getTokenRequestHeader() {
        return tokenRequestHeader;
    }
    
    public void setTokenRequestHeader(String tokenRequestHeader) {
        this.tokenRequestHeader = tokenRequestHeader;
    }
    
    private String tokenRequestMethod;
    
    public String getTokenRequestMethod() {
        return tokenRequestMethod;
    }
    
    public void setTokenRequestMethod(String tokenRequestMethod) {
        this.tokenRequestMethod = tokenRequestMethod;
    }
    
    private String tokenUrl;
    
    public String getTokenUrl() {
        return tokenUrl;
    }
    
    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }
    
    private String tokenRefreshInterval;
    
    public String getTokenRefreshInterval() {
        return tokenRefreshInterval;
    }
    
    public void setTokenRefreshInterval(String tokenRefreshInterval) {
        this.tokenRefreshInterval = tokenRefreshInterval;
    }
    
    private String url;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    // 提供RestfulParam的方法（占位实现）
    public WSDataObjectParamsBean provideRestfulParam() {
        return this;
    }
    
    private String paramId;
    
    public String getParamId() {
        return paramId;
    }
    
    public void setParamId(String paramId) {
        this.paramId = paramId;
    }
}

