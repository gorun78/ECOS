package com.chinacreator.gzcm.engine.ontology;

import java.util.List;
import java.util.Map;

public interface OntologyConfigService {

    Map<String, Object> getDefaults();

    List<Map<String, Object>> getAll();

    List<Map<String, Object>> getByGroup(String group);

    int batchUpdate(List<Map<String, String>> updates);

    void refresh();
}
