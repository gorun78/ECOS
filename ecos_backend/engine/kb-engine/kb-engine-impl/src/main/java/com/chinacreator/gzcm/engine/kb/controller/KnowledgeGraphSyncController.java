package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncLogListVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncRequest;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncResultVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncStatusVO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图形态同步族 Controller — 详设《从零实施版》§7.2.3 ＋ 附录 A 第 15 行
 * （台账《从零实施版》缺失清单第 15 条：{@code triggerGraphSync / getGraphSyncStatus / listGraphSyncLogs}）。
 *
 * <p>三端点（路径/入参/出参逐字承契约）：</p>
 * <ul>
 *   <li><b>{@code POST /api/v1/knowledge/graph/sync}</b>（档 E）→ 200
 *       {@code {attempted,written,notAttempted,reason,reconcileDiff}}，同步投影一次
 *       （PG 权威 → 图库从属副本）；</li>
 *   <li><b>{@code GET /api/v1/knowledge/graph/sync/status}</b>（档 B）→
 *       {@code {graphForm,lastSyncTime,diff}}；</li>
 *   <li><b>{@code GET /api/v1/knowledge/graph/sync/logs}</b>（档 B，分页）→
 *       {@code {items,total,page,size}}，条目含 {@code traceId}。</li>
 * </ul>
 *
 * <p><b>状态行保真（D-B4-59／D-B4-67 同族纪律）</b>：本类不自造失败信封、不做宽 catch 折叠——
 * 投影漂移与投影读数不可得由服务侧抛 {@code ECOS-KB-080}（500），PG／台账读故障由
 * {@code DataAccessException} 上浮，二者统一经 {@code KbErrorAdvice} 单源渲染；读面漂移
 * <b>不改状态行</b>（漂移是被读的对象，200 + {@code graphForm="MISMATCH"} + {@code mismatchCode}）。</p>
 *
 * <p><b>与 {@link GraphSyncController}（{@code /api/v1/knowledge/sync}）的边界</b>：后者是
 * PMO-50 历史运维池（映射抽取触发的异步同步），本条是附录 A 第 15 行的双形态投影族；
 * 两族路由不重叠，语义不同源，禁互相代理。</p>
 *
 * <p><b>如实声明（禁假值）</b>：</p>
 * <ul>
 *   <li>{@code scope} —— 投影恒为全量镜像（PG 唯一权威），该值只作运维标签回显与台账列，
 *       不作为部分镜像的谓词（见 {@link GraphSyncRequest}）；</li>
 *   <li>契约列的 {@code 021}（任务底座 503）—— 本腿为同步投影（runtime-access 批量写），
 *       不经 runtime-task，故不可达；不凑一条假路径；</li>
 *   <li>{@code demo=true} 节点排除 —— 见服务侧声明（{@code graph_node} 无 demo 标记列），
 *       本类不额外伪造过滤。</li>
 * </ul>
 *
 * @group GRAPH
 */
@RestController
@RequestMapping("/api/v1/knowledge/graph/sync")
public class KnowledgeGraphSyncController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphSyncController.class);

    /** Map→VO 映射（键名与服务侧出参同源；容忍未来新增键，语义同 {@link EcosKnowledgeGraphController}）。 */
    private static final ObjectMapper MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 日志分页默认条数（运维面短页；上限由服务侧钳制 200）。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final KgSyncService kgSyncService;

    private final EcosKnowledgeGraphService graphService;

    public KnowledgeGraphSyncController(KgSyncService kgSyncService,
                                        EcosKnowledgeGraphService graphService) {
        this.kgSyncService = kgSyncService;
        this.graphService = graphService;
    }

    /**
     * 触发一次 PG→图库投影（档 E）。同步执行：请求返回时投影与对账均已完成。
     *
     * <p>入参 {@code {scope,fullResync?}}：{@code fullResync=true} 先清空投影子图再全量重写
     * （脏投影的重放手段；带 {@code DETACH DELETE}，只在显式请求时发生）。</p>
     *
     * <p>成功 = 对账一致（{@code graphForm="PROJECTED"}）或本档位无投影能力
     * （{@code graphForm="PG_ONLY"} ＋ {@code notAttempted} ＋ {@code reason}，<b>未尝试不是失败</b>）；
     * 对账漂移／投影计数不可读 ⇒ 500 {@code ECOS-KB-080}（由 {@code KbErrorAdvice} 渲染，
     * 台账行同步标 FAILED——禁「就绪待同步」类伪成功）。</p>
     */
    @PostMapping
    @RequirePermission(value = "触发图形态投影", permission = "knowledge:graph:sync")
    public ResponseEntity<ApiResponse<GraphSyncResultVO>> triggerGraphSync(
            @RequestBody(required = false) GraphSyncRequest body) {
        GraphSyncRequest req = body == null ? new GraphSyncRequest() : body;
        boolean fullResync = Boolean.TRUE.equals(req.getFullResync());
        Map<String, Object> result = kgSyncService.runProjection(req.getScope(), fullResync);
        GraphSyncResultVO vo = MAPPER.convertValue(result, GraphSyncResultVO.class);
        log.info("triggerGraphSync 完成 jobId={} scope={} graphForm={} attempted={} written={} notAttempted={}",
                vo.getJobId(), vo.getScope(), vo.getGraphForm(),
                vo.getAttempted(), vo.getWritten(), vo.getNotAttempted());
        return ResponseEntity.ok(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    /**
     * 图谱双形态只读状态（档 B）：{@code PG_ONLY} / {@code PROJECTED} / {@code MISMATCH}。
     *
     * <p>读面不因漂移变错误（漂移本身是被读的对象）：{@code MISMATCH} 仍 200，body 带
     * {@code mismatchCode=ECOS-KB-080} 与逐类型差异；PG／台账读故障才是错误，上浮 500
     * {@code ECOS-KB-050}（禁空态伪装成「从未同步」）。</p>
     */
    @GetMapping("/status")
    @RequirePermission(value = "读取图谱双形态状态", permission = "knowledge:graph:read")
    public ResponseEntity<ApiResponse<GraphSyncStatusVO>> getGraphSyncStatus() {
        Map<String, Object> status = graphService.graphFormStatus();
        return ResponseEntity.ok(ApiResponse.success(
                MAPPER.convertValue(status, GraphSyncStatusVO.class)).withTraceIdFromMdc());
    }

    /**
     * 同步日志分页（档 B）：{@code ecos_kg_sync_log} 新→旧，条目含 {@code traceId}。
     *
     * <p>分页钳制：{@code page<1 → 1}、{@code size} 钳入 {@code [1,200]}（默认 20）。
     * 台账不可读 → 500 {@code ECOS-KB-050}（禁返回空列表冒充「没有日志」）。</p>
     */
    @GetMapping("/logs")
    @RequirePermission(value = "读取图谱同步日志", permission = "knowledge:graph:read")
    public ResponseEntity<ApiResponse<GraphSyncLogListVO>> listGraphSyncLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Map<String, Object> pageData = kgSyncService.getSyncLogsPage(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                MAPPER.convertValue(pageData, GraphSyncLogListVO.class)).withTraceIdFromMdc());
    }
}
