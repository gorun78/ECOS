package com.chinacreator.gzcm.runtime.core.common.rpccaller.bean;

import java.io.Serializable;

/**
 * FileRpcBean - 文件RPC调用Bean（占位实现）
 * 用于兼容旧代码中的文件RPC调用
 * 
 * @deprecated 建议使用新的RPC调用方式
 */
@Deprecated
public class FileRpcBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String outPath;
    private String outFileName;
    private boolean addDateSuffix;
    private String loginMethod;
    private String krbFilePath;
    private String keytabFilePath;
    private String username;
    private String outEncoding;
    private String outRootElement;
    private String outRowElement;
    private String outSheetName;
    private String outSeparator;
    private String outEnclosure;
    
    public String getOutPath() {
        return outPath;
    }
    
    public void setOutPath(String outPath) {
        this.outPath = outPath;
    }
    
    public String getOutFileName() {
        return outFileName;
    }
    
    public void setOutFileName(String outFileName) {
        this.outFileName = outFileName;
    }
    
    public boolean isAddDateSuffix() {
        return addDateSuffix;
    }
    
    public void setAddDateSuffix(boolean addDateSuffix) {
        this.addDateSuffix = addDateSuffix;
    }
    
    public String getLoginMethod() {
        return loginMethod;
    }
    
    public void setLoginMethod(String loginMethod) {
        this.loginMethod = loginMethod;
    }
    
    public String getKrbFilePath() {
        return krbFilePath;
    }
    
    public void setKrbFilePath(String krbFilePath) {
        this.krbFilePath = krbFilePath;
    }
    
    public String getKeytabFilePath() {
        return keytabFilePath;
    }
    
    public void setKeytabFilePath(String keytabFilePath) {
        this.keytabFilePath = keytabFilePath;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getOutEncoding() {
        return outEncoding;
    }
    
    public void setOutEncoding(String outEncoding) {
        this.outEncoding = outEncoding;
    }
    
    public String getOutRootElement() {
        return outRootElement;
    }
    
    public void setOutRootElement(String outRootElement) {
        this.outRootElement = outRootElement;
    }
    
    public String getOutRowElement() {
        return outRowElement;
    }
    
    public void setOutRowElement(String outRowElement) {
        this.outRowElement = outRowElement;
    }
    
    public String getOutSheetName() {
        return outSheetName;
    }
    
    public void setOutSheetName(String outSheetName) {
        this.outSheetName = outSheetName;
    }
    
    public String getOutSeparator() {
        return outSeparator;
    }
    
    public void setOutSeparator(String outSeparator) {
        this.outSeparator = outSeparator;
    }
    
    public String getOutEnclosure() {
        return outEnclosure;
    }
    
    public void setOutEnclosure(String outEnclosure) {
        this.outEnclosure = outEnclosure;
    }
    
    private String[] fileMask;
    
    public String[] getFileMask() {
        return fileMask;
    }
    
    public void setFileMask(String[] fileMask) {
        this.fileMask = fileMask;
    }
    
    private String[] fileDirs;
    
    public String[] getFileDirs() {
        return fileDirs;
    }
    
    public void setFileDirs(String[] fileDirs) {
        this.fileDirs = fileDirs;
    }
    
    private String encoding;
    
    public String getEncoding() {
        return encoding;
    }
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    private String rowXPath;
    
    public String getRowXPath() {
        return rowXPath;
    }
    
    public void setRowXPath(String rowXPath) {
        this.rowXPath = rowXPath;
    }
    
    private String excelType;
    
    public String getExcelType() {
        return excelType;
    }
    
    public void setExcelType(String excelType) {
        this.excelType = excelType;
    }
    
    private String sheetName;
    
    public String getSheetName() {
        return sheetName;
    }
    
    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }
    
    private Integer startRow;
    
    public Integer getStartRow() {
        return startRow;
    }
    
    public void setStartRow(Integer startRow) {
        this.startRow = startRow;
    }
    
    private Integer startCol;
    
    public Integer getStartCol() {
        return startCol;
    }
    
    public void setStartCol(Integer startCol) {
        this.startCol = startCol;
    }
    
    private String separator;
    
    public String getSeparator() {
        return separator;
    }
    
    public void setSeparator(String separator) {
        this.separator = separator;
    }
    
    private String escapeCharacter;
    
    public String getEscapeCharacter() {
        return escapeCharacter;
    }
    
    public void setEscapeCharacter(String escapeCharacter) {
        this.escapeCharacter = escapeCharacter;
    }
    
    private String enclosure;
    
    public String getEnclosure() {
        return enclosure;
    }
    
    public void setEnclosure(String enclosure) {
        this.enclosure = enclosure;
    }
    
    private boolean[] includeSubdirs;
    
    public boolean[] getIncludeSubdirs() {
        return includeSubdirs;
    }
    
    public void setIncludeSubdirs(boolean[] includeSubdirs) {
        this.includeSubdirs = includeSubdirs;
    }
    
    private String dataFileHandlerType;
    
    public String getDataFileHandlerType() {
        return dataFileHandlerType;
    }
    
    public void setDataFileHandlerType(String dataFileHandlerType) {
        this.dataFileHandlerType = dataFileHandlerType;
    }
    
    private String removePath;
    
    public String getRemovePath() {
        return removePath;
    }
    
    public void setRemovePath(String removePath) {
        this.removePath = removePath;
    }
}
