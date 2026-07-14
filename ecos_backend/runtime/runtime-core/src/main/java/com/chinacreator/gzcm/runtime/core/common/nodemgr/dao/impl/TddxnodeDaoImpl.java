package com.chinacreator.gzcm.runtime.core.common.nodemgr.dao.impl;

import com.chinacreator.gzcm.runtime.core.common.nodemgr.dao.ITddxnodeDao;
import com.chinacreator.gzcm.runtime.core.core.bean.Tddxnode;
import java.util.ArrayList;
import java.util.List;

public class TddxnodeDaoImpl implements ITddxnodeDao {

    @Override
    public Tddxnode getNodeById(String nodeId) throws Exception {
        Tddxnode node = new Tddxnode();
        node.setNode_id(nodeId);
        node.setInner_ip("127.0.0.1");
        return node;
    }

    @Override
    public List<Tddxnode> getAllNodes() throws Exception {
        return new ArrayList<>();
    }

    @Override
    public void addNode(Tddxnode node) throws Exception {
        // Mock implementation
    }

    @Override
    public void updateNode(Tddxnode node) throws Exception {
        // Mock implementation
    }

    @Override
    public void deleteNode(String nodeId) throws Exception {
        // Mock implementation
    }
}
