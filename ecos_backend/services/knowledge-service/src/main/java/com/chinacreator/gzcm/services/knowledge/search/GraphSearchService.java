package com.chinacreator.gzcm.services.knowledge.search;

import com.chinacreator.gzcm.services.knowledge.rag.DocumentChunk;
import java.util.List;

public interface GraphSearchService {
    List<DocumentChunk> searchByGraph(String query, int topK);
}
