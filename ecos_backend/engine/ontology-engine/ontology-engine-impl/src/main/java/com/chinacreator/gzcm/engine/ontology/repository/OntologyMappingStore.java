package com.chinacreator.gzcm.engine.ontology.repository;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;

/**
 * 本体映射共享存储 — 供 OntologyMappingController 和 OntologyService 共享。
 * 解决 entityToMap() 中 mapping 字段始终为 null 的问题。
 */
@Component
public class OntologyMappingStore {
    public final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();
}
