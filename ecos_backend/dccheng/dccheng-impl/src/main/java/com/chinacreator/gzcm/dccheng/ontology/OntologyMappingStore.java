package com.chinacreator.gzcm.dccheng.ontology;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 本体映射共享存储 — 供 OntologyMappingController 和 OntologyService 共享。
 * 解决 entityToMap() 中 mapping 字段始终为 null 的问题。
 */
@Component
public class OntologyMappingStore {
    public final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();
}
