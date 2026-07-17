package com.chinacreator.gzcm.services.knowledge.search;

import com.chinacreator.gzcm.services.knowledge.rag.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphSearchServiceImpl implements GraphSearchService {
    private static final Logger log = LoggerFactory.getLogger(GraphSearchServiceImpl.class);

    @Override
    public List<DocumentChunk> searchByGraph(String query, int topK) {
        log.info("Graph search for: {} topK={}", query, topK);
        return new ArrayList<>();
    }
}
