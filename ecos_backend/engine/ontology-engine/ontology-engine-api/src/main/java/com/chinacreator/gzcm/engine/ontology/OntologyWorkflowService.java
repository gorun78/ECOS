package com.chinacreator.gzcm.engine.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OntologyWorkflowService {

    List<Map<String, Object>> listDefinitions(int pageSize);

    Map<String, Object> createDefinition(Map<String, Object> body);

    Optional<Map<String, Object>> getDefinition(String id);

    List<Map<String, Object>> listInstances(int limit);

    Optional<Map<String, Object>> startInstance(String workflowId, Map<String, Object> body);

    Optional<Map<String, Object>> getInstance(String instanceId);

    Map<String, Object> approve(String taskId, Map<String, Object> body);

    Map<String, Object> reject(String taskId, Map<String, Object> body);
}
