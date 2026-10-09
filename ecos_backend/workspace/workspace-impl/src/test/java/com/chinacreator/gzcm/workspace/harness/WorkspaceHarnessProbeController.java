package com.chinacreator.gzcm.workspace.harness;

import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.exception.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.exception.IllegalScenarioTransitionException;
import com.chinacreator.gzcm.workspace.exception.IslandBindingException;
import com.chinacreator.gzcm.workspace.exception.OptimisticLockConflictException;
import com.chinacreator.gzcm.workspace.exception.SandboxReferenceForbiddenException;
import com.chinacreator.gzcm.workspace.exception.ScenarioNotFoundException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceDomainException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卷07 F07-24 测试底座探针（仅 test 源集）。
 *
 * <p>用途：在薄切片里把 §四 C-5 异常→HTTP 映射表的每一行都真实跑一遍 dispatcher，
 * 证明 {@code WorkspaceExceptionHandler} 返回的是<b>真状态码</b>而非 200 装 5xx
 * （X-16/X-17 反假阳性）。它不是业务端点，不进入任何生产路由面。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/_harness")
public class WorkspaceHarnessProbeController {

    /** 八类领域异常 + 一类内部异常的可控抛出点。 */
    @GetMapping("/throw/{kind}")
    public Map<String, Object> throwDomain(@PathVariable("kind") String kind) {
        throw switch (kind) {
            case "island" -> new IslandBindingException(List.of(Map.of("nodeId", "n-1")));
            case "transition" -> IllegalScenarioTransitionException.illegal("DRAFT", "COMPLETED");
            case "terminal" -> IllegalScenarioTransitionException.terminal("COMPLETED");
            case "no-formal-run" -> IllegalScenarioTransitionException.noFormalRun();
            case "sandbox" -> new SandboxReferenceForbiddenException("run-sandbox-1");
            case "cognitive" -> new CognitiveEngineUnavailableException("engine down");
            case "forbidden" -> new ForbiddenOperationException("scenario:s-1");
            case "lock" -> new OptimisticLockConflictException("s-1", "7");
            case "notfound" -> new ScenarioNotFoundException("s-1");
            case "action" -> new ActionValidationException(List.of("metric", "runMode"));
            // 内部异常原文（SQL/连接串）必须被 advice 屏蔽在响应体外
            default -> new DataAccessResourceFailureException("jdbc:postgresql://secret-host:5432/sys_man password=hunter2");
        };
    }

    /** 起动探针 —— 只在没有异常时可达，用于证明切片真的在服务请求。 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("exceptionClassOfBase", WorkspaceDomainException.class.getSimpleName());
        return m;
    }
}
