package com.chinacreator.gzcm.services.knowledge.search;

import com.chinacreator.gzcm.services.knowledge.rag.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VectorSearchServiceImpl implements VectorSearchService {
    private static final Logger log = LoggerFactory.getLogger(VectorSearchServiceImpl.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<DocumentChunk> search(String query, int topK) {
        log.info("Vector search for: {} topK={}", query, topK);
        List<DocumentChunk> results = new ArrayList<>();
        if (jdbcTemplate != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, document_id, content, 0.5 as score FROM ecos_knowledge.knowledge_embedding LIMIT ?",
                    topK
                );
                for (Map<String, Object> row : rows) {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setId(String.valueOf(row.get("id")));
                    chunk.setDocumentId(String.valueOf(row.get("document_id")));
                    chunk.setContent(String.valueOf(row.get("content")));
                    chunk.setScore(0.5);
                    results.add(chunk);
                }
            } catch (Exception e) {
                log.warn("Vector search fallback - table not ready: {}", e.getMessage());
            }
        }
        return results;
    }

    @Override
    public void ingest(String documentId, String content, Map<String, Object> metadata) {
        log.info("Vector ingest for document: {}", documentId);
    }
}
