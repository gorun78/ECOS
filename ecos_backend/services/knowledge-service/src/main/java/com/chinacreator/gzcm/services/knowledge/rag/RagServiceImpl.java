package com.chinacreator.gzcm.services.knowledge.rag;

import com.chinacreator.gzcm.services.knowledge.search.VectorSearchService;
import com.chinacreator.gzcm.services.knowledge.search.GraphSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagServiceImpl implements RagService {
    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    @Autowired(required = false)
    private VectorSearchService vectorSearchService;

    @Autowired(required = false)
    private GraphSearchService graphSearchService;

    @Override
    public RagResponse query(RagRequest request) {
        log.info("RAG query: {}", request.getQuery());
        RagResponse response = new RagResponse();
        response.setQuery(request.getQuery());
        List<DocumentChunk> chunks = new ArrayList<>();

        if (request.isUseVector() && vectorSearchService != null) {
            List<DocumentChunk> vectorResults = vectorSearchService.search(request.getQuery(), request.getTopK());
            chunks.addAll(vectorResults);
        }

        if (request.isUseGraph() && graphSearchService != null) {
            List<DocumentChunk> graphResults = graphSearchService.searchByGraph(request.getQuery(), request.getTopK());
            chunks.addAll(graphResults);
        }

        if (chunks.isEmpty()) {
            response.setAnswer("No relevant information found for: " + request.getQuery());
            response.setConfidence(0.0);
        } else {
            StringBuilder sb = new StringBuilder();
            for (DocumentChunk chunk : chunks) {
                sb.append(chunk.getContent()).append("\n");
            }
            response.setAnswer(sb.toString());
            response.setSources(chunks);
            response.setConfidence(chunks.get(0).getScore());
        }

        return response;
    }

    @Override
    public void ingestDocument(String documentId, String content, Map<String, Object> metadata) {
        log.info("Ingesting document: {}", documentId);
        if (vectorSearchService != null) {
            vectorSearchService.ingest(documentId, content, metadata);
        }
    }
}
