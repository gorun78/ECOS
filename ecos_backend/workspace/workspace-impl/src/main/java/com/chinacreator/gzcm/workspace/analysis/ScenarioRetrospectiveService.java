package com.chinacreator.gzcm.workspace.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;

/**
 * 场景复盘（V367 批次 C）：运行记录 + 认知侧按 run 聚合，两段独立降级。
 *
 * <p>运行段来自 {@code ecos_scenario_run}（问题/意图/假设/算法/执行载体/轨迹/建议）；
 * 认知段来自 {@code GET /cognitive/mental-reviews/by-run/{runId}}。认知不可用时只把该段标
 * {@code unavailable=true}，运行段照常返回——不白屏、不伪造认知结论。</p>
 */
@Service
public class ScenarioRetrospectiveService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRetrospectiveService.class);

    private final WorkspaceDataDao dao;
    private final DcchengClient cognitive;

    public ScenarioRetrospectiveService(WorkspaceDataDao dao, DcchengClient cognitive) {
        this.dao = dao;
        this.cognitive = cognitive;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> build(String scenarioId, String runId) {
        List<Map<String, Object>> rows = dao.findQuestionRun(runId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "SCENARIO-RETRO-404: 运行不存在: " + runId);
        }
        Map<String, Object> run = rows.get(0);
        if (!scenarioId.equals(String.valueOf(run.get("scenario_id")))) {
            throw new BusinessException(404, "SCENARIO-RETRO-404: 运行不属于该场景: " + runId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("runId", runId);
        out.put("run", run);
        out.put("algorithm", run.get("algorithm_result"));

        try {
            out.put("cognitive", cognitive.mentalReviewByRun(runId));
        } catch (RuntimeException e) {
            log.warn("场景复盘：认知聚合不可用 run={} err={}", runId, e.getMessage());
            Map<String, Object> unavailable = new LinkedHashMap<>();
            unavailable.put("unavailable", true);
            unavailable.put("message", e.getMessage());
            out.put("cognitive", unavailable);
        }

        Map<String, Object> previous = findPrevious(scenarioId, runId, String.valueOf(run.get("question")));
        out.put("previous", previous);
        return out;
    }

    /** 同问题上一条运行（只给定位信息，不做数值重算）。 */
    private Map<String, Object> findPrevious(String scenarioId, String runId, String question) {
        try {
            for (Map<String, Object> row : dao.listQuestionRuns(scenarioId, 20)) {
                String id = String.valueOf(row.get("id"));
                if (id.equals(runId)) continue;
                if (question != null && question.equals(String.valueOf(row.get("question")))) {
                    Map<String, Object> prev = new LinkedHashMap<>();
                    prev.put("runId", id);
                    prev.put("mentalModel", row.get("mental_model"));
                    prev.put("algorithmStatus", row.get("algorithm_status"));
                    prev.put("status", row.get("status"));
                    prev.put("createTime", row.get("create_time"));
                    return prev;
                }
            }
        } catch (RuntimeException e) {
            log.warn("场景复盘：历史运行读取失败: {}", e.getMessage());
        }
        return Map.of();
    }
}
