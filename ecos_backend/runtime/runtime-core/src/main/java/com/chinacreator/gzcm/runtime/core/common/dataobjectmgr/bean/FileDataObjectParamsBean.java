package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * FileDataObjectParamsBean class
 * TODO: Add proper implementation based on actual requirements
 */
public class FileDataObjectParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String regexp;
    
    public String getRegexp() {
        return regexp;
    }
    
    public void setRegexp(String regexp) {
        this.regexp = regexp;
    }
    
    private String encode;
    
    public String getEncode() {
        return encode;
    }
    
    public void setEncode(String encode) {
        this.encode = encode;
    }
    
    private String rootElement;
    
    public String getRootElement() {
        return rootElement;
    }
    
    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }
    
    private String rowElement;
    
    public String getRowElement() {
        return rowElement;
    }
    
    public void setRowElement(String rowElement) {
        this.rowElement = rowElement;
    }
    
    private String excelSheet;
    
    public String getExcelSheet() {
        return excelSheet;
    }
    
    public void setExcelSheet(String excelSheet) {
        this.excelSheet = excelSheet;
    }
    
    private String separator;
    
    public String getSeparator() {
        return separator;
    }
    
    public void setSeparator(String separator) {
        this.separator = separator;
    }
    
    private String enclosure;
    
    public String getEnclosure() {
        return enclosure;
    }
    
    public void setEnclosure(String enclosure) {
        this.enclosure = enclosure;
    }
    
    private String xpath;
    
    public String getXpath() {
        return xpath;
    }
    
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }
    
    private Integer startRow;
    
    public Integer getStartRow() {
        return startRow;
    }
    
    public void setStartRow(Integer startRow) {
        this.startRow = startRow;
    }
    
    private Integer startColumn;
    
    public Integer getStartColumn() {
        return startColumn;
    }
    
    public void setStartColumn(Integer startColumn) {
        this.startColumn = startColumn;
    }
    
    private String escapeCharacter;
    
    public String getEscapeCharacter() {
        return escapeCharacter;
    }
    
    public void setEscapeCharacter(String escapeCharacter) {
        this.escapeCharacter = escapeCharacter;
    }
    
    private String applyToChild;
    
    public String getApplyToChild() {
        return applyToChild;
    }
    
    public void setApplyToChild(String applyToChild) {
        this.applyToChild = applyToChild;
    }
    
    private String paramId;
    
    public String getParamId() {
        return paramId;
    }
    
    public void setParamId(String paramId) {
        this.paramId = paramId;
    }
    
    private String dsFolder;
    
    public String getDsFolder() {
        return dsFolder;
    }
    
    public void setDsFolder(String dsFolder) {
        this.dsFolder = dsFolder;
    }
}

