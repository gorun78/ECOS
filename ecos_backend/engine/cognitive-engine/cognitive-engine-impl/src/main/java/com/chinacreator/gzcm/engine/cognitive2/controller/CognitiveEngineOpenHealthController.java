package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveEngineOpenHealthVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveEngineOpenHealthVO.Components;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * cognitive 引擎公开的 Open Health 端点 — PMO-55 第 4 波 E-C 产物。
 *
 * <p>背景：cognitive2 的 {@link CognitiveEngineHealthController} 原映射
 * {@code /api/v1/cognitive/health}，与 ai-engine 的 {@code CognitiveConfigController}
 * （PMO-51 权威配置端点）同前缀冲突，被 {@code GatewayApplication} 的
 * {@code excludeFilters} 排除（避免 Ambiguous mapping）。E-A 的
 * {@code KnowledgeHealthAggregator} 因此对 cognitive slot REST 探活恒为
 * DOWN（remote timeout）。</p>
 *
 * <p>解法（改法 A，更稳）：保留 cognitive2 原版 {@link CognitiveEngineHealthController}
 * 的排除设计不动，新增本 Controller 用全新前缀 {@code /api/v1/engine/cognitive}
 * 接管公开 health 查询。新前缀与 ai-engine 的 {@code /api/v1/cognitive/config}
 * 完全不冲突；与 kb-engine 的 {@code /api/v1/engine/knowledge} 前缀也完全不冲突。</p>
 *
 * <p>探测内容：</p>
 * <ul>
 *   <li>{@code db} — JdbcTemplate 探活 PG (sys_man)</li>
 *   <li>{@code pipelineCount} — {@code kb_cognitive_pipeline} 行数（仅未删除 is_deleted=0）</li>
 *   <li>{@code pipelineActiveCount} — {@code kb_cognitive_pipeline} 中 status='ACTIVE' 行数</li>
 * </ul>
 *
 * <p>状态判定（详见 {@link CognitiveEngineOpenHealthVO}）：</p>
 * <ul>
 *   <li>{@code UP} — DB 探活成功 + 两个 COUNT 均成功</li>
 *   <li>{@code DEGRADED} — DB 探活成功 + 任一个 COUNT 失败（表未建、schema 变化等）</li>
 *   <li>{@code DOWN} — DB 探活失败（带 reason，截断 200 字）</li>
 * </ul>
 *
 * <p>Bean name 加 {@code ecos} 前缀（架构铁律 §1.3：新 Bean 加 ecos 前缀避免 default
 * name 冲突）。JdbcTemplate 构造器注入（铁律 §1.3：JdbcTemplate 必须构造器注入，
 * 不绕 @Autowired 走字段注入）。</p>
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-C (2026-09-13)
 */
@RestController("ecosCognitiveEngineOpenHealthController")
@RequestMapping("/api/v1/engine/cognitive")
public class CognitiveEngineOpenHealthController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveEngineOpenHealthController.class);

    /** 引擎版本标识 — 用于前端 / 上游聚合器识别版本。 */
    static final String VERSION = "v2.0";

    /** Spring 托管的 JdbcTemplate（指向 sys_man PG）。 */
    private final JdbcTemplate jdbcTemplate;

    /** 服务器启动（JVM/Web 启动）时间戳，用于计算 uptimeMs。 */
    private volatile long startTimestamp = System.currentTimeMillis();

    /**
     * 构造器注入 JdbcTemplate（铁律 §1.3）。
     *
     * @param jdbcTemplate Spring 托管的 JdbcTemplate Bean（由 gateway 启动时装配，指向 sys_man）
     */
    public CognitiveEngineOpenHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Web 服务器初始化事件回调 — 校准 startTimestamp 为真实启动时间。
     * <p>{@code WebServerInitializedEvent} 在 Tomcat 绑定端口后触发，
     * 保证 uptimeMs 反映真实服务时长（Bean 实例化时间会早于绑定，存在偏差）。</p>
     *
     * @param event WebServer 初始化事件
     */
    @EventListener
    public void onWebServerStarted(WebServerInitializedEvent event) {
        this.startTimestamp = System.currentTimeMillis();
        log.info("CognitiveEngineOpenHealthController: web server started on port {}",
                event.getWebServer().getPort());
    }

    /**
     * GET /api/v1/engine/cognitive/health — cognitive 引擎公开健康检查。
     *
     * <p>独立于 ai-engine 的 {@code /api/v1/cognitive/health}
     * （该路径的 cognitive2 Controller 副本被 GatewayApplication excludeTokens 屏蔽）。
     * 独立于 ai 的 {@code CognitiveConfigController}（权威配置端点，保留不动）。</p>
     *
     * <p>JDBC 查询两段 try-catch 包裹：DB 探活 + 两条 COUNT。
     * 异常不外抛，仅记录 logger warn 并降级 VO 字段（铁律 §1.4：禁止 throws Exception）。
     * 不抛 BusinessException — health 端点本就是"探活"性质，按 design 返回降级 VO 即可，
     * 不阻断调用方。</p>
     *
     * @return 统一 {@link ApiResponse}，data 为 {@link CognitiveEngineOpenHealthVO}：
     *         {@code {status, components{pipelineCount, pipelineActiveCount, db}, uptimeMs, version, reason?}}
     */
    @GetMapping("/health")
    public ApiResponse<CognitiveEngineOpenHealthVO> health() {
        long uptimeMs = System.currentTimeMillis() - startTimestamp;
        Components components = new Components();
        components.setDb("DOWN");
        components.setPipelineCount(-1L);
        components.setPipelineActiveCount(-1L);

        boolean dbUp = false;
        String dbReason = null;

        // ── 1) PG DB 探活 ──
        try {
            Long one = jdbcTemplate.queryForObject("SELECT 1", Long.class);
            dbUp = (one != null && one == 1L);
            components.setDb(dbUp ? "UP" : "DOWN");
        } catch (DataAccessException ex) {
            dbReason = "db-conn: " + shortMsg(ex);
            log.warn("cognitive open health: db probe failed: {}", shortMsg(ex));
        }

        // ── 2) pipeline 计数（仅在 DB UP 时）──
        if (dbUp) {
            boolean countFailed = false;
            try {
                Long total = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM kb_cognitive_pipeline WHERE is_deleted = 0",
                        Long.class);
                components.setPipelineCount(total == null ? 0L : total);
            } catch (DataAccessException ex) {
                countFailed = true;
                components.setPipelineCount(-1L);
                log.warn("cognitive open health: pipelineCount query failed (table missing or schema drift): {}",
                        shortMsg(ex));
            }
            try {
                Long active = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM kb_cognitive_pipeline WHERE status = 'ACTIVE' AND is_deleted = 0",
                        Long.class);
                components.setPipelineActiveCount(active == null ? 0L : active);
            } catch (DataAccessException ex) {
                countFailed = true;
                components.setPipelineActiveCount(-1L);
                log.warn("cognitive open health: pipelineActiveCount query failed: {}", shortMsg(ex));
            }
        }

        // ── 3) 组装 VO + 状态判定 ──
        CognitiveEngineOpenHealthVO vo = new CognitiveEngineOpenHealthVO();
        vo.setComponents(components);
        vo.setUptimeMs(uptimeMs);
        vo.setVersion(VERSION);
        if (dbUp && components.getPipelineCount() != -1L && components.getPipelineActiveCount() != -1L) {
            vo.setStatus("UP");
        } else if (dbUp) {
            vo.setStatus("DEGRADED");
        } else {
            vo.setStatus("DOWN");
            vo.setReason(dbReason == null ? "db-conn" : dbReason);
        }
        return ApiResponse.success(vo);
    }

    /**
     * 截断异常 short msg（避免日志超长）。
     */
    private static String shortMsg(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg = msg + ": " + t.getMessage();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
