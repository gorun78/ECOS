package com.chinacreator.gzcm.engine.ontology;

import java.util.Map;

public interface OntologyCopilotService {

    Map<String, Object> suggestEntity(String prompt, String schemaInfo);

    Map<String, Object> suggestRelation(String prompt, String schemaInfo);

    Map<String, Object> validateConsistency(String schemaInfo);

    Map<String, Object> reverseImport(String schemaInfo);
}
