package com.chinacreator.gzcm.runtime.core.datadescription.service;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;

/**
 * 鏁版嵁鎻忚堪鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface IDataDescriptionService {
    
    /**
     * 鍒涘缓鏁版嵁鎻忚堪
     * 
     * @param description 鏁版嵁鎻忚堪瀵硅薄
     * @return 鍒涘缓鐨勬暟鎹弿杩板璞★紙鍖呭惈鐢熸垚鐨処D锛?
     * @throws Exception
     */
    DataDescription createDataDescription(DataDescription description) throws Exception;
    
    /**
     * 鏇存柊鏁版嵁鎻忚堪
     * 
     * @param id 鏁版嵁鎻忚堪ID
     * @param description 鏁版嵁鎻忚堪瀵硅薄
     * @return 鏇存柊鍚庣殑鏁版嵁鎻忚堪瀵硅薄
     * @throws Exception
     */
    DataDescription updateDataDescription(String id, DataDescription description) throws Exception;
    
    /**
     * 鏍规嵁ID鑾峰彇鏁版嵁鎻忚堪
     * 
     * @param id 鏁版嵁鎻忚堪ID
     * @return 鏁版嵁鎻忚堪瀵硅薄
     * @throws Exception
     */
    DataDescription getDataDescription(String id) throws Exception;
    
    /**
     * 鍒犻櫎鏁版嵁鎻忚堪
     * 
     * @param id 鏁版嵁鎻忚堪ID
     * @throws Exception
     */
    void deleteDataDescription(String id) throws Exception;
    
    /**
     * 鍒楀嚭鏁版嵁鎻忚堪
     * 
     * @param condition 鏌ヨ鏉′欢
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescription> listDataDescriptions(QueryCondition condition) throws Exception;
    
    /**
     * 鏍规嵁鏁版嵁绫诲瀷鏌ヨ鏁版嵁鎻忚堪
     * 
     * @param dataType 鏁版嵁绫诲瀷
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescription> listDataDescriptionsByType(String dataType) throws Exception;
    
    /**
     * 鏌ヨ鏉′欢
     */
    class QueryCondition {
        private String dataType;
        private String name;
        private String format;
        private Integer page;
        private Integer pageSize;
        private String sortField;
        private String sortOrder;
        private Integer offset; // 鍒嗛〉鍋忕Щ閲?
        
        // Getters and Setters
        public String getDataType() {
            return dataType;
        }
        
        public void setDataType(String dataType) {
            this.dataType = dataType;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getFormat() {
            return format;
        }
        
        public void setFormat(String format) {
            this.format = format;
        }
        
        public Integer getPage() {
            return page;
        }
        
        public void setPage(Integer page) {
            this.page = page;
        }
        
        public Integer getPageSize() {
            return pageSize;
        }
        
        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }
        
        public String getSortField() {
            return sortField;
        }
        
        public void setSortField(String sortField) {
            this.sortField = sortField;
        }
        
        public String getSortOrder() {
            return sortOrder;
        }
        
        public void setSortOrder(String sortOrder) {
            this.sortOrder = sortOrder;
        }
        
        public Integer getOffset() {
            return offset;
        }
        
        public void setOffset(Integer offset) {
            this.offset = offset;
        }
    }
}

