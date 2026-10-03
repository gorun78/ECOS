package com.chinacreator.gzcm.engine.kb.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.kb.profile.CuratedFactSource.CuratedFactRow;
import com.chinacreator.gzcm.engine.kb.profile.ProfileStatsCalculator.Result;
import com.chinacreator.gzcm.engine.kb.shared.KbActorSupport;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.PageVO;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史画像服务（F04-06 / REQ-KB-02）。
 * <p>
 * 域规则：
 * <ul>
 *   <li>取数只读 CURATED {@code dq_status=PUBLISHED}（B-1 禁读 RAW）；</li>
 *   <li>统计为纯算法（{@link ProfileStatsCalculator}），本服务是唯一允许做分布计算的 Service；</li>
 *   <li>置信度阈值 n≥30 HIGH / 10≤n&lt;30 MEDIUM；n&lt;10 沿退化链向粗粒度升级；</li>
 *   <li>退化链：项目×部门×环节 → 项目类型×部门×环节 → 项目类型×环节 → 部门×环节 → 全局×环节（五级）；</li>
 *   <li>版本单调递增（{@link ProfileKeys#nextVersion}），已发布资产 <b>不可修改</b>（409 KB_061），
 *       重新生成新版本时旧 PUBLISHED 置 SUPERSEDED（I-6 语义）；</li>
 *   <li>发布需知识管理员（403 KB_062，K-41）；</li>
 *   <li>生成一律经 {@code ITaskManagementService}（F04-14，K-38/K-39）；本类直接暴露 dryRun 的同步
 *       内核 {@link #generateSync} 供 TaskExecutor 与 dry-run 契约路径复用（不直接 Executors/CompletableFuture）。</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
@Service
public class KbProfileServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(KbProfileServiceImpl.class);

    private final KbProfileMapper profileMapper;
    private final KbProfileStatsMapper statsMapper;
    private final CuratedFactSource factSource;
    /** runtime-task 底座（F04-14 收口）；null 时 {@link #generate} 抛 KB_021，禁降级同步。 */
    private final ITaskManagementService taskService;

    public KbProfileServiceImpl(KbProfileMapper profileMapper,
                                KbProfileStatsMapper statsMapper,
                                CuratedFactSource factSource,
                                ITaskManagementService taskService) {
        this.profileMapper = profileMapper;
        this.statsMapper = statsMapper;
        this.factSource = factSource;
        this.taskService = taskService;
    }

    // ── 状态/置信常量 ──────────────────────────────────────────────────────────
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String CONF_HIGH = "HIGH";
    public static final String CONF_MEDIUM = "MEDIUM";
    public static final String CONF_LOW = "LOW";
    public static final String STATS_METHOD = "T_APPROX";
    public static final int DEGRADE_THRESHOLD = 10;      // n<10 → 沿退化链升级
    public static final int HIGH_THRESHOLD = 30;         // n>=30 → HIGH
    public static final String TASK_TYPE_PROFILE = "KB_PROFILE_GENERATE";

    // ── 内存摘录（generateSync 结果，可单测断言不落库路径） ───────────────────
    public static class GenerationResult {
        public final long generated;
        public final long degraded;
        public final long insufficient;
        public final List<String> errorCodes;

        GenerationResult(long generated, long degraded, long insufficient, List<String> errorCodes) {
            this.generated = generated;
            this.degraded = degraded;
            this.insufficient = insufficient;
            this.errorCodes = errorCodes;
        }
    }

    // ── 生成入口 ──────────────────────────────────────────────────────────────

    /**
     * 提交生成任务（D.3 POST /profiles/generate）。只提交，不本地执行；不阻塞。
     * <p>dryRun=true 直接同步执行并返回 taskId=null（不落库不占任务中心）；
     * dryRun=false 走 {@code ITaskManagementService.submitTask}（若 taskService 不可用 → KB_021 503）。
     */
    public Map<String, Object> generate(ProfileGenerateRequest req) {
        validate(req);
        if (req.isDryRun()) {
            GenerationResult r = generateSync(req, true);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("taskId", null);
            out.put("status", "DRY_RUN_DONE");
            out.put("traceId", TraceContext.current());
            out.put("generated", r.generated);
            out.put("degraded", r.degraded);
            out.put("insufficient", r.insufficient);
            out.put("errorCodes", r.errorCodes);
            return out;
        }
        if (taskService == null) {
            throw KbErrorCode.ex(KbErrorCode.KB_021, "runtime-task 不可用，画像生成禁降级同步（F04-14）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("metricCodes", req.getMetricCodes());
        params.put("dims", req.getDims());
        params.put("windowFrom", req.getWindowFrom());
        params.put("windowTo", req.getWindowTo());
        TaskDescription td = new TaskDescription();
        td.setTaskName("kb-profile-generate");
        td.setTaskType(TASK_TYPE_PROFILE);
        td.setDescription("历史画像生成（F04-06）");
        td.setCreatedBy(KbActorSupport.currentActor());
        td.setPriority(0);
        td.setParameters(params);
        td.setAsync(Boolean.FALSE);
        try {
            String taskId = taskService.submitTask(td);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("taskId", taskId);
            out.put("status", "SUBMITTED");
            out.put("traceId", TraceContext.current());
            return out;
        } catch (TaskManagementException e) {
            log.warn("submitTask 失败: {}", e.getMessage());
            throw KbErrorCode.ex(KbErrorCode.KB_021, "runtime-task 提交失败: " + e.getMessage());
        }
    }

    /**
     * 同步生成内核（真实写入路径，dryRun=false 的 TaskExecutor 与 dryRun=true 的契约路径共用）。
     * <p>只读 CURATED 事实 → 每 metric 起算五级退化链 → 命中首个 n≥10 的粒度生成 P10/P50/P90/CI
     * 并落画像表 + 写 stats 数值事实（stats_ref 关联）→ 生成 DRAFT 行；若五级全 n<10 → KB_041。
     */
    public GenerationResult generateSync(ProfileGenerateRequest req, boolean dryRun) {
        validate(req);
        String traceId = TraceContext.current();
        String actor = KbActorSupport.currentActor();
        String domain = "ecos_knowledge";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, String> reqDims = toStrMap(req.getDims());
        long generated = 0, degraded = 0, insufficient = 0;
        List<String> errorCodes = new ArrayList<>();
        for (String metric : req.getMetricCodes()) {
            List<CuratedFactRow> rows;
            try {
                rows = fetchCurated(metric, req);
            } catch (Exception e) {
                // C.3 CURATED 供数不可达 → 502（ECOS-KB-040）
                errorCodes.add(KbErrorCode.KB_040);
                log.warn("CURATED 取数失败 metric={} err={}", metric, e.getMessage());
                continue;
            }
            Result r = null;
            int hitGrainIdx = -1;
            Map<String, String> hitDims = null;
            for (int i = 0; i < ProfileKeys.DEGRADE_CHAIN_GRAINS.size(); i++) {
                String[] grain = ProfileKeys.DEGRADE_CHAIN_GRAINS.get(i);
                Map<String, String> used = pickGrainValues(reqDims, grain);
                List<CuratedFactRow> projected = applyGrain(rows, grain);
                Result cand = ProfileStatsCalculator.compute(valuesOf(projected));
                if (cand.n() < DEGRADE_THRESHOLD) {
                    continue;
                }
                r = cand;
                hitGrainIdx = i;
                hitDims = used;
                if (i > 0) {
                    degraded++;
                }
                break;
            }
            if (r == null) {
                insufficient++;
                errorCodes.add(KbErrorCode.KB_041);
                continue;
            }
            String key = ProfileKeys.buildKey(metric, hitDims);
            if (!dryRun) {
                persistOne(metric, hitDims, key, r, hitGrainIdx, req, traceId, actor, domain, now);
            }
            generated++;
        }
        return new GenerationResult(generated, degraded, insufficient, errorCodes);
    }

    /** 只读 CURATED 事实（禁读 RAW），异常抛出由调用方映射为 KB_040。 */
    protected List<CuratedFactRow> fetchCurated(String metric, ProfileGenerateRequest req) {
        String ref = "curated|metric=" + metric
                + "|window=" + req.getWindowFrom() + "~" + req.getWindowTo();
        List<CuratedFactRow> rows = factSource.fetch(metric, req.getWindowFrom(), req.getWindowTo(), ref);
        return rows == null ? List.of() : rows;
    }

    /**
     * 持久化生成的一条画像（dryRun=false）。
     * <ul>
     *   <li>先写 stats 数值事实（R-8 ②：数值落业务域 ecos_dw）；</li>
     *   <li>再写画像定义态（ecos_knowledge），stats_ref 关联；</li>
     *   <li>profile_version 单调递增（nextVersion）；</li>
     *   <li>degradeChain = 退化链截至命中粒度的各级可读名；degradeFrom = 上一级粒度可读名（i==0 时 null）。</li>
     * </ul>
     */
    private void persistOne(String metric, Map<String, String> hitDims, String key,
                            Result r, int hitGrainIdx, ProfileGenerateRequest req, String traceId,
                            String actor, String domain, Timestamp now) {
        String id = KbActorSupport.newId();
        String statsId = KbActorSupport.newId();
        int sampleCount = (int) Math.min(Integer.MAX_VALUE, r.sampleCount);

        statsMapper.insert(statsId, id, key, sampleCount, r.missingRate,
                r.p10, r.p50, r.p90, r.mean, r.ciLow, r.ciHigh,
                req.getWindowFrom(), req.getWindowTo(), statsId, traceId, domain,
                actor, actor, now, now);

        Map<String, Object> latest = profileMapper.findLatestVersionByKey(key);
        String latestVersion = latest == null ? null : obj(latest.get("profileVersion"));
        String version = ProfileKeys.nextVersion(latestVersion);
        String confidence = r.n() >= HIGH_THRESHOLD ? CONF_HIGH : CONF_MEDIUM;
        String degradeFrom = hitGrainIdx > 0
                ? ProfileKeys.DEGRADE_CHAIN_NAMES[hitGrainIdx - 1] : null;
        List<String> degradeChain = degradeChainForGrainIdx(hitGrainIdx);
        String degradePathJson = toSimpleJson(degradeChain);
        String dimsJson = toSimpleJson(hitDims);

        profileMapper.insert(id, key, metric, dimsJson,
                req.getWindowFrom(), req.getWindowTo(), statsId,
                "95", STATS_METHOD, confidence, degradeFrom, degradePathJson,
                null, "curated|metric=" + metric,
                version, STATUS_DRAFT, null, null,
                null, traceId, domain, "1", 0, actor, actor, now, now);
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    public ProfileResolveVO resolve(String metric, String dimsFilter, String atVersion) {
        Map<String, Object> row = profileMapper.findByKeyForResolve(keyForMetricFilter(metric, dimsFilter), atVersion);
        if (row == null) {
            return null;
        }
        ProfileResolveVO vo = new ProfileResolveVO();
        vo.setProfileId(str2(row.get("id")));
        vo.setProfileKey(str2(row.get("profileKey")));
        vo.setProfileVersion(str2(row.get("profileVersion")));
        vo.setConfidence(str2(row.get("confidence")));
        vo.setDegradeChain(parseChain(str2(row.get("degradePathJson"))));
        vo.setTraceId(str2(row.get("traceId")));
        Object statsRef = row.get("statsRef");
        if (statsRef != null) {
            Map<String, Object> s = statsMapper.findById(str2(statsRef));
            if (s != null) {
                vo.setStats(new ProfileStatsVO(
                        asLong(s.get("sampleCount")), asBd(s.get("missingRate")),
                        asBd(s.get("p10")), asBd(s.get("p50")), asBd(s.get("p90")),
                        asBd(s.get("meanValue")), asBd(s.get("ciLow")), asBd(s.get("ciHigh")),
                        STATS_METHOD));
            }
        }
        return vo;
    }

    public PageVO<ProfileVO> list(String metric, String dimsFilter, String status, int page, int size) {
        int off = (PageVO.clampPage(page) - 1) * PageVO.clampSize(size);
        int lim = PageVO.clampSize(size);
        long total = profileMapper.count(metric, dimsFilter, status);
        List<Map<String, Object>> rows = profileMapper.list(metric, dimsFilter, status, off, lim);
        List<ProfileVO> rows2 = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            rows2.add(toVO(r));
        }
        return new PageVO<>(rows2, total, PageVO.clampPage(page), lim);
    }

    // ── 发布 / 修改 ──────────────────────────────────────────────────────────
    /** 发布。DRAFT→PUBLISHED，同 key 旧 PUBLISHED 置 SUPERSEDED（I-6 版本语义 + 不可变）。 */
    public void publish(String id, String approver) {
        if (!KbActorSupport.isKnowledgeAdmin()) {
            throw KbErrorCode.ex(KbErrorCode.KB_062, "非知识管理员不可发布画像（F04-06）");
        }
        Map<String, Object> row = profileMapper.findById(id);
        if (row == null) {
            throw new KbErrorCode.KbErrorCodeException(404, "ECOS-KB-060", "画像不存在: " + id);
        }
        String status = str2(row.get("status"));
        if (!STATUS_DRAFT.equals(status)) {
            // 已 PUBLISHED/SUPERSEDED → 不可变（I-6）
            throw KbErrorCode.ex(KbErrorCode.KB_061, "已发布画像不可变（I-6）：status=" + status);
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        int n = profileMapper.markPublished(id, approver, now, approver, now);
        if (n == 0) {
            throw KbErrorCode.ex(KbErrorCode.KB_060, "状态机非法跃迁：非 DRAFT");
        }
        String key = str2(row.get("profileKey"));
        profileMapper.supersedePublishedByKey(key, approver, now);
    }

    /** 覆修改：拒改已 PUBLISHED（I-6）——单一语义合入 {@link #publish}，本方法留作 409 显式调用点。 */
    public void rejectUpdateIfPublished(String id) {
        Map<String, Object> row = profileMapper.findById(id);
        if (row != null && STATUS_PUBLISHED.equals(str2(row.get("status")))) {
            throw KbErrorCode.ex(KbErrorCode.KB_061, "已发布画像不可变（I-6）");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void validate(ProfileGenerateRequest req) {
        if (req == null || req.getMetricCodes() == null || req.getMetricCodes().isEmpty()) {
            throw KbErrorCode.ex(KbErrorCode.KB_012, "metricCodes 必填（F04-06）");
        }
        if (isBlank(req.getWindowFrom()) || isBlank(req.getWindowTo())) {
            throw KbErrorCode.ex(KbErrorCode.KB_012, "windowFrom/windowTo 必填（F04-06）");
        }
    }

    /** 依 grain 取值：grain 列若在 reqDims 有值则用之（specs），否则让退化投影把细分列合并。 */
    private static Map<String, String> pickGrainValues(Map<String, String> reqDims, String[] grain) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String g : grain) {
            if (reqDims != null && reqDims.containsKey(g) && reqDims.get(g) != null) {
                out.put(g, reqDims.get(g));
            }
        }
        return out;
    }

    /** 依 grain 投影：grain 中所有行都有 key 才保留；粗粒度共享组则取组值。 */
    static List<CuratedFactRow> applyGrain(List<CuratedFactRow> rows, String[] grain) {
        if (rows == null) return List.of();
        List<CuratedFactRow> out = new ArrayList<>(rows.size());
        for (CuratedFactRow r : rows) {
            if (grainSatisfied(r, grain)) {
                out.add(r);
            }
        }
        return out;
    }

    private static boolean grainSatisfied(CuratedFactRow r, String[] grain) {
        for (String g : grain) {
            if (g.equals("project") && isBlank(r.getProject())) return false;
            if (g.equals("projectType") && isBlank(r.getProjectType())) return false;
            if (g.equals("department") && isBlank(r.getDepartment())) return false;
            // stage 保留为分组轴但不作为纳入条件（"全局×环节"级别不要求 project/ptype/dept）
        }
        return true;
    }

    static List<BigDecimal> valuesOf(List<CuratedFactRow> rows) {
        List<BigDecimal> out = new ArrayList<>(rows.size());
        for (CuratedFactRow r : rows) {
            out.add(r.getValue());
        }
        return out;
    }

    /** 退化链截至命中粒度（含）的可读名列表；hitIdx 越界时钳制。 */
    static List<String> degradeChainForGrainIdx(int hitIdx) {
        int hi = Math.min(Math.max(hitIdx, 0), ProfileKeys.DEGRADE_CHAIN_GRAINS.size() - 1);
        List<String> chain = new ArrayList<>();
        for (int i = 0; i <= hi; i++) {
            chain.add(ProfileKeys.DEGRADE_CHAIN_NAMES[i]);
        }
        return chain;
    }

    static String toSimpleJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return "[" + String.join(",", list) + "]";
    }

    static String toSimpleJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        return String.join("\u0001", parts);  // 内部用不可见分隔（同事 mapper LIKE 匹配）
    }

    /** Metric (+ dims) → profile_key（resolve 时 dims 常为 null）。 */
    static String keyForMetricFilter(String metric, String dimsFilter) {
        if (metric == null || metric.isBlank()) return "";
        if (dimsFilter == null || dimsFilter.isBlank()) return metric;
        return metric + "|" + dimsFilter;
    }

    private static ProfileVO toVO(Map<String, Object> r) {
        ProfileVO vo = new ProfileVO();
        vo.setId(str2(r.get("id")));
        vo.setProfileKey(str2(r.get("profileKey")));
        vo.setMetricCode(str2(r.get("metricCode")));
        vo.setGroupDimsJson(str2(r.get("groupDimsJson")));
        vo.setWindowFrom(str2(r.get("windowFrom")));
        vo.setWindowTo(str2(r.get("windowTo")));
        vo.setStatsRef(str2(r.get("statsRef")));
        vo.setCiLevel(str2(r.get("ciLevel")));
        vo.setStatsMethod(str2(r.get("statsMethod")));
        vo.setConfidence(str2(r.get("confidence")));
        vo.setDegradeFrom(str2(r.get("degradeFrom")));
        String chain = str2(r.get("degradePathJson"));
        vo.setDegradeChain(parseChain(chain));
        vo.setSourceQueryRef(str2(r.get("sourceQueryRef")));
        vo.setProfileVersion(str2(r.get("profileVersion")));
        vo.setStatus(str2(r.get("status")));
        vo.setApprovedBy(str2(r.get("approvedBy")));
        vo.setTaskId(str2(r.get("taskId")));
        vo.setTraceId(str2(r.get("traceId")));
        vo.setDomain(str2(r.get("domain")));
        return vo;
    }

    private static List<String> parseChain(String json) {
        if (json == null || json.isBlank()) return List.of();
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isBlank()) return List.of();
        String[] parts = inner.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private static Map<String, String> toStrMap(Map<String, Object> m) {
        if (m == null) return new LinkedHashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return out;
    }

    private static String str2(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static BigDecimal asBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static String obj(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
