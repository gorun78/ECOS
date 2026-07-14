package com.chinacreator.gzcm.engine.cognitive;

import java.util.Map;

public interface PromptCompilerService {

    Map<String, Object> compileContext(Map<String, Object> req);

    Map<String, Object> getIndexStatus();
}
