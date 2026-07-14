package com.chinacreator.gzcm.engine.ontology;

import java.util.Map;

public interface OntologyGitService {

    Map<String, Object> commit(String ontologyId, Map<String, Object> body);

    Map<String, Object> pull(String ontologyId, Map<String, Object> body);

    Map<String, Object> load(Map<String, Object> body);
}
