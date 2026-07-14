package com.chinacreator.gzcm.runtime.core.common.rmiservice.bean;

import java.io.Serializable;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;

/**
 * QueryInfo - 查询信息Bean类
 * 用于封装查询请求和结果信息
 */
public class QueryInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Tddxdatasource ds;
    private String sql;
    private String wsdlURI;
    private String method;
    private String targetNameSpace;
    private String paramsAndValues;
    private boolean issuccess;
    private Object resultObj;
    private String errorMessage;
    private long total_records;
    private long return_records;
    
    // Getters and setters
    public Tddxdatasource getDs() {
        return ds;
    }
    
    public void setDs(Tddxdatasource ds) {
        this.ds = ds;
    }
    
    /**
     * 设置数据源（重载方法，兼容DatasourceBean）
     * @param dsBean DatasourceBean对象
     */
    public void setDs(com.chinacreator.gzcm.runtime.core.common.dbdata.bean.DatasourceBean dsBean) {
        // Convert DatasourceBean to Tddxdatasource
        if (dsBean != null) {
            Tddxdatasource tddxds = new Tddxdatasource();
            tddxds.setDs_name(dsBean.getDs_name());
            tddxds.setDb_type(""); // Set appropriate db_type if available
            this.ds = tddxds;
        } else {
            this.ds = null;
        }
    }
    
    public String getSql() {
        return sql;
    }
    
    public void setSql(String sql) {
        this.sql = sql;
    }
    
    public String getWsdlURI() {
        return wsdlURI;
    }
    
    public void setWsdlURI(String wsdlURI) {
        this.wsdlURI = wsdlURI;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getTargetNameSpace() {
        return targetNameSpace;
    }
    
    public void setTargetNameSpace(String targetNameSpace) {
        this.targetNameSpace = targetNameSpace;
    }
    
    public String getParamsAndValues() {
        return paramsAndValues;
    }
    
    public void setParamsAndValues(String paramsAndValues) {
        this.paramsAndValues = paramsAndValues;
    }
    
    /**
     * 设置参数和值（重载方法，兼容Map类型）
     * @param paramsMap 参数Map
     */
    public void setParamsAndValues(Map<String, String> paramsMap) {
        if (paramsMap == null || paramsMap.isEmpty()) {
            this.paramsAndValues = null;
            return;
        }
        // Convert Map to string format (simple implementation)
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        this.paramsAndValues = sb.toString();
    }
    
    public boolean isIssuccess() {
        return issuccess;
    }
    
    public void setIssuccess(boolean issuccess) {
        this.issuccess = issuccess;
    }
    
    public Object getResultObj() {
        return resultObj;
    }
    
    public void setResultObj(Object resultObj) {
        this.resultObj = resultObj;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * Alias for setErrorMessage() for compatibility
     */
    public void setMsginfo(String msgInfo) {
        this.errorMessage = msgInfo;
    }
    
    /**
     * Alias for getErrorMessage() for compatibility
     */
    public String getMsginfo() {
        return errorMessage;
    }
    
    public long getTotal_records() {
        return total_records;
    }
    
    public void setTotal_records(long total_records) {
        this.total_records = total_records;
    }
    
    public long getReturn_records() {
        return return_records;
    }
    
    public void setReturn_records(long return_records) {
        this.return_records = return_records;
    }
    
    private boolean ispage;
    
    public boolean isIspage() {
        return ispage;
    }
    
    public void setIspage(boolean ispage) {
        this.ispage = ispage;
    }
    
    private long recordbegin;
    
    public long getRecordbegin() {
        return recordbegin;
    }
    
    public void setRecordbegin(long recordbegin) {
        this.recordbegin = recordbegin;
    }
    
    private int recordsize;
    
    public int getRecordsize() {
        return recordsize;
    }
    
    public void setRecordsize(int recordsize) {
        this.recordsize = recordsize;
    }
    
    // Additional fields for compatibility
    private boolean data;
    private String[][] preparePargrams;
    private boolean filter;
    
    public boolean isData() {
        return data;
    }
    
    public void setData(boolean data) {
        this.data = data;
    }
    
    public String[][] getPreparePargrams() {
        return preparePargrams;
    }
    
    public void setPreparePargrams(String[][] preparePargrams) {
        this.preparePargrams = preparePargrams;
    }
    
    public boolean isFilter() {
        return filter;
    }
    
    public void setFilter(boolean filter) {
        this.filter = filter;
    }
    
    // Additional field for compatibility
    private String[] requestItems;
    private Map<String, String> codes;
    
    public String[] getRequestItems() {
        return requestItems;
    }
    
    public void setRequestItems(String[] requestItems) {
        this.requestItems = requestItems;
    }
    
    public Map<String, String> getCodes() {
        return codes;
    }
    
    public void setCodes(Map<String, String> codes) {
        this.codes = codes;
    }
    
    // Additional field for return label
    private Map<String, String> returnLabel;
    
    public Map<String, String> getReturnLabel() {
        return returnLabel;
    }
    
    public void setReturnLabel(Map<String, String> returnLabel) {
        this.returnLabel = returnLabel;
    }
}

