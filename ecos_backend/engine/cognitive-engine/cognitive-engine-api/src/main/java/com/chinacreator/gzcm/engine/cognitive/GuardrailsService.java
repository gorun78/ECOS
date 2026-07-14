package com.chinacreator.gzcm.engine.cognitive;

import java.util.List;
import java.util.Map;

public interface GuardrailsService {

    Map<String, Object> validate(Map<String, Object> req);

    List<Map<String, Object>> listPolicies();

    Map<String, Object> createPolicy(Map<String, Object> policy);

    void deletePolicy(String id);
}
