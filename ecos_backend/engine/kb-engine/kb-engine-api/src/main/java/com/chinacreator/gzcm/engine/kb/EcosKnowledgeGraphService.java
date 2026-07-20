package com.chinacreator.gzcm.engine.kb;

import java.util.Map;

public interface EcosKnowledgeGraphService {

    Map<String, Object> getGraphSnapshot();

    Map<String, Object> syncToNeo4j();
}