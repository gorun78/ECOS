package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.engine.IEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kb")
public class KbEngineHealthController {

    private static final Logger log = LoggerFactory.getLogger(KbEngineHealthController.class);

    @Autowired
    @Qualifier("kbEngineImpl")
    private IEngine kbEngine;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("engine", "KNOWLEDGE");
        status.put("name", kbEngine.getName());
        status.put("engineStatus", kbEngine.getStatus().name());
        status.putAll(kbEngine.healthCheck().getComponents());
        return status;
    }
}
