package com.chinacreator.gzcm.runtime.core.core.controlcenter;

import com.chinacreator.gzcm.runtime.core.core.bean.Tddxnode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeighborNodeCache {
    private static NeighborNodeCache instance = new NeighborNodeCache();
    private Map<String, Tddxnode> nodeCache = new ConcurrentHashMap<>();

    private NeighborNodeCache() {}

    public static NeighborNodeCache getInstance() {
        return instance;
    }

    public Tddxnode getNodeById(String nodeId) {
        if (nodeCache.containsKey(nodeId)) {
            return nodeCache.get(nodeId);
        }
        // Mock return for compilation if not found
        Tddxnode mockNode = new Tddxnode();
        mockNode.setNode_id(nodeId);
        mockNode.setInner_ip("127.0.0.1");
        return mockNode;
    }

    public void putNode(Tddxnode node) {
        if (node != null && node.getNode_id() != null) {
            nodeCache.put(node.getNode_id(), node);
        }
    }

    public List<Tddxnode> getClusterNodeChilds(String nodeId) {
        List<Tddxnode> result = new ArrayList<>();
        // Mock implementation: return empty list for compilation
        // In real implementation, this would query child nodes of the cluster logic node
        return result;
    }
}
