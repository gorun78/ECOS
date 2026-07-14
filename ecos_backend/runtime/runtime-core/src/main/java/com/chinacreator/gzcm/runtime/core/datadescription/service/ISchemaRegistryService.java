package com.chinacreator.gzcm.runtime.core.datadescription.service;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaRegistry;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;

/**
 * Schema Registry鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface ISchemaRegistryService {
    
    /**
     * 娉ㄥ唽Schema
     * 
     * @param subject Schema涓婚
     * @param schema Schema瀵硅薄
     * @return 娉ㄥ唽鍚庣殑Schema鐗堟湰淇℃伅
     * @throws Exception
     */
    SchemaRegistry registerSchema(String subject, DataSchema schema) throws Exception;
    
    /**
     * 娉ㄥ唽Schema锛堝甫鍏煎鎬ф鏌ワ級
     * 
     * @param subject Schema涓婚
     * @param schema Schema瀵硅薄
     * @param checkCompatibility 鏄惁妫€鏌ュ吋瀹规€?
     * @return 娉ㄥ唽鍚庣殑Schema鐗堟湰淇℃伅
     * @throws Exception
     */
    SchemaRegistry registerSchema(String subject, DataSchema schema, boolean checkCompatibility) throws Exception;
    
    /**
     * 鏍规嵁subject鍜寁ersion鑾峰彇Schema
     * 
     * @param subject Schema涓婚
     * @param version 鐗堟湰鍙?
     * @return Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    SchemaRegistry getSchema(String subject, Integer version) throws Exception;
    
    /**
     * 鑾峰彇鏈€鏂扮増鏈殑Schema
     * 
     * @param subject Schema涓婚
     * @return 鏈€鏂扮増鏈殑Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    SchemaRegistry getLatestSchema(String subject) throws Exception;
    
    /**
     * 鍒楀嚭鎸囧畾subject鐨勬墍鏈夌増鏈?
     * 
     * @param subject Schema涓婚
     * @return 鐗堟湰鍙峰垪琛?
     * @throws Exception
     */
    List<Integer> listVersions(String subject) throws Exception;
    
    /**
     * 鍒楀嚭鎸囧畾subject鐨勬墍鏈塖chema
     * 
     * @param subject Schema涓婚
     * @return Schema鍒楄〃
     * @throws Exception
     */
    List<SchemaRegistry> listSchemas(String subject) throws Exception;
    
    /**
     * 鍒犻櫎Schema
     * 
     * @param subject Schema涓婚
     * @param version 鐗堟湰鍙凤紙濡傛灉涓簄ull锛屽垯鍒犻櫎鎵€鏈夌増鏈級
     * @throws Exception
     */
    void deleteSchema(String subject, Integer version) throws Exception;
}

