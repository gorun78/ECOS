package com.chinacreator.gzcm.services.ontology.controller;

import com.chinacreator.gzcm.services.ontology.compiler.CompilationRequest;
import com.chinacreator.gzcm.services.ontology.compiler.CompilationResult;
import com.chinacreator.gzcm.services.ontology.compiler.OntologyCompiler;
import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ontology/compiler")
public class OntologyCompilerController {

    @Autowired
    private OntologyCompiler compiler;

    @PostMapping("/compile")
    public ApiResponse<CompilationResult> compile(@RequestBody CompilationRequest request) {
        return ApiResponse.success(compiler.compile(request));
    }
}
