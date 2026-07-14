package com.chinacreator.gzcm.sysman.compliance.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.compliance.entity.CrossBorderTransfer;

/**
 * 跨境传输DAO接口
 */
public interface CrossBorderTransferDao {
    void insert(CrossBorderTransfer transfer) throws Exception;
    
    /**
     * 查询跨境传输记录
     * 
     * @param condition 查询条件
     * @return 传输记录列表
     * @throws Exception
     */
    List<CrossBorderTransfer> query(Map<String, Object> condition) throws Exception;
}

