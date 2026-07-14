package com.chinacreator.gzcm.engine.ontology;

import java.util.Map;

public interface OntologyGraphService {

    Map<String, Object> getOntologyGraph(String ontologyId);

    Map<String, Object> getFullGraph();

    Map<String, Object> traceNode(String nodeId);
}
