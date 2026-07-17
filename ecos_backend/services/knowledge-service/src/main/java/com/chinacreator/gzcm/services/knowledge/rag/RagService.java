package com.chinacreator.gzcm.services.knowledge.rag;

import java.util.List;
import java.util.Map;

public interface RagService {
    RagResponse query(RagRequest request);
    void ingestDocument(String documentId, String content, Map<String, Object> metadata);
}
