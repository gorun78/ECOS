package com.chinacreator.gzcm.services.knowledge.controller;

import com.chinacreator.gzcm.services.knowledge.rag.RagRequest;
import com.chinacreator.gzcm.services.knowledge.rag.RagResponse;
import com.chinacreator.gzcm.services.knowledge.rag.RagService;
import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeRagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/rag")
    public ApiResponse<RagResponse> ragQuery(@RequestBody RagRequest request) {
        return ApiResponse.success(ragService.query(request));
    }

    @PostMapping("/ingest")
    public ApiResponse<Void> ingestDocument(@RequestParam String documentId, @RequestBody String content, @RequestParam Map<String, Object> metadata) {
        ragService.ingestDocument(documentId, content, metadata);
        return ApiResponse.success(null);
    }
}
