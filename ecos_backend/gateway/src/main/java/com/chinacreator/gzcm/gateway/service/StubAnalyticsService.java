package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.common.service.IAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Profile("!enterprise & !flagship")
public class StubAnalyticsService implements IAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(StubAnalyticsService.class);
    
    @Override
    public List<Map<String, Object>> executeQuery(String analyticsSql) {
        log.debug("StubAnalyticsService.executeQuery: {}", analyticsSql);
        return List.of();
    }
    
    @Override
    public Map<String, Object> health() {
        return Map.of("analyticsProvider", "stub", "status", "noop");
    }
}
