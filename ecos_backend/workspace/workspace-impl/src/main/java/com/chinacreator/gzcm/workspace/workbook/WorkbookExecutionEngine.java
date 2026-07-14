package com.chinacreator.gzcm.workspace.workbook;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Workbook 多语言执行引擎 — 统一入口。
 * <p>
 * 根据 language 路由到对应的 Runtime：
 * <ul>
 *   <li>{@code sql} → {@link SqlRuntime}</li>
 *   <li>{@code python} → {@link PythonRuntime}</li>
 *   <li>{@code r} → {@link RRuntime}</li>
 * </ul>
 */
@Service
public class WorkbookExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkbookExecutionEngine.class);

    private final SqlRuntime sqlRuntime;
    private final PythonRuntime pythonRuntime;
    private final RRuntime rRuntime;

    public WorkbookExecutionEngine(SqlRuntime sqlRuntime, PythonRuntime pythonRuntime, RRuntime rRuntime) {
        this.sqlRuntime = sqlRuntime;
        this.pythonRuntime = pythonRuntime;
        this.rRuntime = rRuntime;
    }

    /**
     * 执行代码片段。
     *
     * @param language  语言: sql / python / r
     * @param code      源代码
     * @param sessionId 会话 ID（python 需要用于工作目录）
     * @return 执行结果 Map，包含 columns/rows/elapsed_ms/stdout 等
     */
    public Map<String, Object> execute(String language, String code, String sessionId) {
        log.info("Workbook execute: language={}, sessionId={}, codeLength={}", language, sessionId,
                code != null ? code.length() : 0);

        return switch (language.toLowerCase()) {
            case "sql" -> sqlRuntime.execute(code);
            case "python" -> pythonRuntime.execute(code, sessionId);
            case "r" -> rRuntime.execute(code);
            default -> throw new IllegalArgumentException("Unknown language: " + language
                    + ". Supported: sql, python, r");
        };
    }
}
