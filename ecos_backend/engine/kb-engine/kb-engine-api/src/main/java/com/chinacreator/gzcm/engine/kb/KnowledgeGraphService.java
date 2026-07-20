package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;

import java.util.List;
import java.util.Map;

public interface KnowledgeGraphService {

    Map<String, Object> getGraph(String domain);

    Map<String, Object> getNodeDetail(String nodeId);

    List<KnowledgeNode> search(String query);

    Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId);

    Map<String, Object> getNeighbors(String nodeId, int degree);

    KnowledgeNode createNode(String label, String nodeType, String description, String propertiesJson);

    KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId, String relationship, double weight);

    String getDataSource();
}