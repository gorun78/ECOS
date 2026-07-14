package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine/ontology/git")
public class OntologyGitController {

    private final OntologyGitService gitService;

    public OntologyGitController(OntologyGitService gitService) {
        this.gitService = gitService;
    }

    @PostMapping("/commit/{ontologyId}")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String ontologyId,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> result = gitService.commit(ontologyId, body);
        return ApiResponse.success(result);
    }

    @PostMapping("/pull/{ontologyId}")
    public ApiResponse<Map<String, Object>> pull(@PathVariable String ontologyId,
                                                  @RequestBody Map<String, Object> body) {
        Map<String, Object> result = gitService.pull(ontologyId, body);
        return ApiResponse.success(result);
    }

    @PostMapping("/load")
    public ApiResponse<Map<String, Object>> load(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = gitService.load(body);
        return ApiResponse.success(result);
    }
}
