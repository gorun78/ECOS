package com.chinacreator.gzcm.runtime.core.datadescription.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaRegistry;
import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaVersion;

/**
 * Schema Registry DAO鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface SchemaRegistryDao {
    
    /**
     * 鏍规嵁subject鍜寁ersion鏌ヨSchema
     * 
     * @param subject Schema涓婚
     * @param version 鐗堟湰鍙?
     * @return Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    SchemaRegistry findBySubjectAndVersion(String subject, Integer version) throws Exception;
    
    /**
     * 鏌ヨ鎸囧畾subject鐨勬渶鏂扮増鏈琒chema
     * 
     * @param subject Schema涓婚
     * @return 鏈€鏂扮増鏈殑Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    SchemaRegistry findLatestBySubject(String subject) throws Exception;
    
    /**
     * 鏌ヨ鎸囧畾subject鐨勬墍鏈夌増鏈琒chema
     * 
     * @param subject Schema涓婚
     * @return Schema鍒楄〃
     * @throws Exception
     */
    List<SchemaRegistry> findAllBySubject(String subject) throws Exception;
    
    /**
     * 淇濆瓨Schema娉ㄥ唽淇℃伅
     * 
     * @param schema Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    void save(SchemaRegistry schema) throws Exception;
    
    /**
     * 鏇存柊Schema娉ㄥ唽淇℃伅
     * 
     * @param schema Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    void update(SchemaRegistry schema) throws Exception;
    
    /**
     * 鍒犻櫎Schema娉ㄥ唽淇℃伅
     * 
     * @param schema Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    void delete(SchemaRegistry schema) throws Exception;
    
    /**
     * 鏍规嵁ID鏌ヨSchema
     * 
     * @param id Schema ID
     * @return Schema娉ㄥ唽淇℃伅
     * @throws Exception
     */
    SchemaRegistry findById(String id) throws Exception;
    
    /**
     * 鏌ヨ鎸囧畾subject鐨勬渶澶х増鏈彿
     * 
     * @param subject Schema涓婚
     * @return 鏈€澶х増鏈彿锛屽鏋滀笉瀛樺湪鍒欒繑鍥?
     * @throws Exception
     */
    Integer findMaxVersionBySubject(String subject) throws Exception;
    
    /**
     * 淇濆瓨Schema鐗堟湰淇℃伅
     * 
     * @param schemaVersion Schema鐗堟湰淇℃伅
     * @throws Exception
     */
    void saveVersion(SchemaVersion schemaVersion) throws Exception;
    
    /**
     * 鏌ヨ鎸囧畾subject鐨勬墍鏈夌増鏈彿
     * 
     * @param subject Schema涓婚
     * @return 鐗堟湰鍙峰垪琛?
     * @throws Exception
     */
    List<Integer> findVersionsBySubject(String subject) throws Exception;
}

