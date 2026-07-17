package com.chinacreator.gzcm.services.knowledge.search;

import com.chinacreator.gzcm.services.knowledge.rag.DocumentChunk;
import java.util.List;
import java.util.Map;

public interface VectorSearchService {
    List<DocumentChunk> search(String query, int topK);
    void ingest(String documentId, String content, Map<String, Object> metadata);
}
