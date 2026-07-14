package com.chinacreator.gzcm.runtime.core.common.nodemgr.dao;

import com.chinacreator.gzcm.runtime.core.core.bean.Tddxnode;
import java.util.List;

public interface ITddxnodeDao {
    Tddxnode getNodeById(String nodeId) throws Exception;
    List<Tddxnode> getAllNodes() throws Exception;
    void addNode(Tddxnode node) throws Exception;
    void updateNode(Tddxnode node) throws Exception;
    void deleteNode(String nodeId) throws Exception;
}
