package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Run 预算门面（详细设计-10 F10-11 / §4.4 / V265 seed {@code config_group='wagent.budget'}）。
 *
 * <p><b>配置单源</b>：取值一律经 sysman {@link SysConfigService} 门面，禁 @Value 散落默认值、
 * 禁私读表。缺省取代码冻结默认（V265 六键），运维可<b>下调不可上调</b> ⇒
 * 生效值 = {@code min(代码冻结默认, 配置值)}（§4.4 口径；上调请求视同无效，仍按冻结默认）。
 * sysman 未装配（单测切片/启动早期）时回退冻结默认并记 WARN。</p>
 *
 * <p>数字不来自 LLM：预算判定与消耗计数全部是本类的确定性整数比较。</p>
 */
@Component
public class WAgentBudgetService {

    private static final Logger log = LoggerFactory.getLogger(WAgentBudgetService.class);

    /** V265 seed 六键键名单源（config_group='wagent.budget'）。 */
    public static final String KEY_MAX_STEPS = "wagent.budget.max_steps";
    public static final String KEY_MAX_LLM_CALLS = "wagent.budget.max_llm_calls";
    public static final String KEY_SYNC_TIMEOUT_SECONDS = "wagent.budget.sync_timeout_seconds";
    public static final String KEY_ASYNC_TIMEOUT_MINUTES = "wagent.budget.async_timeout_minutes";
    public static final String KEY_MAX_REPLAN_ROUNDS = "wagent.budget.max_replan_rounds";
    public static final String KEY_MAX_REFILL_ROUNDS = "wagent.budget.max_refill_rounds";

    /** 代码冻结默认（V265 seed 同值；亦是上调防线 min 的左值）。 */
    public static final int FREEZE_MAX_STEPS = 60;
    public static final int FREEZE_MAX_LLM_CALLS = 40;
    public static final int FREEZE_SYNC_TIMEOUT_SECONDS = 60;
    public static final int FREEZE_ASYNC_TIMEOUT_MINUTES = 30;
    public static final int FREEZE_MAX_REPLAN_ROUNDS = 3;
    public static final int FREEZE_MAX_REFILL_ROUNDS = 3;

    private final ObjectProvider<SysConfigService> sysConfigProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WAgentBudgetService(ObjectProvider<SysConfigService> sysConfigProvider) {
        this.sysConfigProvider = sysConfigProvider;
    }

    /** 单键生效值 = min(冻结默认, 配置值)（可下调不可上调）。 */
    public int effective(String key, int frozenDefault) {
        SysConfigService cfg = sysConfigProvider.getIfAvailable();
        if (cfg == null) {
            log.warn("SysConfigService 未装配，预算键 {} 回退代码冻结默认 {}（禁 @Value 散落）", key, frozenDefault);
            return frozenDefault;
        }
        int configured = cfg.getInt(key, frozenDefault);
        return Math.min(frozenDefault, configured);
    }

    /** 六键快照（Run 创建时冻结进 budget_json，恢复重放读快照不重读配置，保证确定性）。 */
    public BudgetSnapshot capture() {
        return new BudgetSnapshot(
                effective(KEY_MAX_STEPS, FREEZE_MAX_STEPS),
                effective(KEY_MAX_LLM_CALLS, FREEZE_MAX_LLM_CALLS),
                effective(KEY_SYNC_TIMEOUT_SECONDS, FREEZE_SYNC_TIMEOUT_SECONDS),
                effective(KEY_ASYNC_TIMEOUT_MINUTES, FREEZE_ASYNC_TIMEOUT_MINUTES),
                effective(KEY_MAX_REPLAN_ROUNDS, FREEZE_MAX_REPLAN_ROUNDS),
                effective(KEY_MAX_REFILL_ROUNDS, FREEZE_MAX_REFILL_ROUNDS));
    }

    /** 快照写库形态（budget_json）。 */
    public String toSnapshotJson(BudgetSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot.asMap());
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("budget_json 序列化失败");
        }
    }

    /**
     * 消耗写库形态（consumption_json）——与 {@link #toSnapshotJson} 同族的写侧单源。
     *
     * <p>键名单源 = {@link Consumption#asMap()}；读侧单源仍在 {@code StepExecutor#parseConsumption}
     * （既有 4 个调用点在用，不迁）。本方法只服务新增写入方（F10-09 补齐轮次回写），
     * 禁调用点自己拼 {@code {"refill_rounds":n}} 字面量。</p>
     */
    public String toConsumptionJson(Consumption consumption) {
        try {
            return objectMapper.writeValueAsString(consumption.asMap());
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("consumption_json 序列化失败");
        }
    }

    /** 快照读回（null/脏值 ⇒ 现值重取——新 Run 语义，恢复路径由调用方保证有值）。 */
    public BudgetSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return capture();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new BudgetSnapshot(
                    intOr(m, "max_steps", FREEZE_MAX_STEPS),
                    intOr(m, "max_llm_calls", FREEZE_MAX_LLM_CALLS),
                    intOr(m, "sync_timeout_seconds", FREEZE_SYNC_TIMEOUT_SECONDS),
                    intOr(m, "async_timeout_minutes", FREEZE_ASYNC_TIMEOUT_MINUTES),
                    intOr(m, "max_replan_rounds", FREEZE_MAX_REPLAN_ROUNDS),
                    intOr(m, "max_refill_rounds", FREEZE_MAX_REFILL_ROUNDS));
        } catch (WAgentOrchestrationException e) {
            throw e;
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("budget_json 不可解析（禁宽容当默认）");
        }
    }

    /**
     * 超时判定（F10-11：sync 60s / async 30min）——纯时长比较。
     *
     * @param async true=异步通道（30min 档）；同步通道 60s 档到点由调用方转
     *              {@code timed_out} 后以 HTTP 200 + 终态返回（§5.3：禁把超时包成 500）
     */
    public boolean isTimedOut(LocalDateTime startTime, BudgetSnapshot budget, boolean async) {
        if (startTime == null) {
            return false;
        }
        Duration limit = async
                ? Duration.ofMinutes(budget.asyncTimeoutMinutes())
                : Duration.ofSeconds(budget.syncTimeoutSeconds());
        return Duration.between(startTime, LocalDateTime.now()).compareTo(limit) > 0;
    }

    /** 预算越限文案（E-WA-BUDGET 429 诊断线索；对外文案仍经错误码渲染）。 */
    public static WAgentOrchestrationException stepBudgetExceeded(int observed, int cap) {
        return WAgentOrchestrationException.budget(
                "Step 数越限: " + observed + " > " + cap + "（" + KEY_MAX_STEPS + "）");
    }

    /** 运行态消耗计数（consumption_json 载体；步/LLM 调用/重规划/补槽四计数器）。 */
    public record Consumption(int steps, int llmCalls, int replanRounds, int refillRounds) {

        public static Consumption zero() {
            return new Consumption(0, 0, 0, 0);
        }

        public Consumption plusSteps(int n) {
            return new Consumption(steps + n, llmCalls, replanRounds, refillRounds);
        }

        public Consumption plusLlmCalls(int n) {
            return new Consumption(steps, llmCalls + n, replanRounds, refillRounds);
        }

        public Consumption plusReplanRounds(int n) {
            return new Consumption(steps, llmCalls, replanRounds + n, refillRounds);
        }

        public Consumption plusRefillRounds(int n) {
            return new Consumption(steps, llmCalls, replanRounds, refillRounds + n);
        }

        /** 子 Run 消耗原子并入父 Run（F10-14：sub_run 消费归集，四计数逐项相加）。 */
        public Consumption merge(Consumption other) {
            if (other == null) {
                return this;
            }
            return new Consumption(steps + other.steps(), llmCalls + other.llmCalls(),
                    replanRounds + other.replanRounds(), refillRounds + other.refillRounds());
        }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("steps", steps);
            m.put("llm_calls", llmCalls);
            m.put("replan_rounds", replanRounds);
            m.put("refill_rounds", refillRounds);
            return Collections.unmodifiableMap(m);
        }
    }

    /** 预算快照值对象（六键生效值，创建时冻结）。 */
    public record BudgetSnapshot(int maxSteps, int maxLlmCalls, int syncTimeoutSeconds,
                                 int asyncTimeoutMinutes, int maxReplanRounds, int maxRefillRounds) {

        /** budget_json 载体键（与 {@link #parseSnapshot} 读回键单源同名；配置键点号形态只属于 sysman）。 */
        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("max_steps", maxSteps);
            m.put("max_llm_calls", maxLlmCalls);
            m.put("sync_timeout_seconds", syncTimeoutSeconds);
            m.put("async_timeout_minutes", asyncTimeoutMinutes);
            m.put("max_replan_rounds", maxReplanRounds);
            m.put("max_refill_rounds", maxRefillRounds);
            return Collections.unmodifiableMap(m);
        }

        /** Step 主计数闸（F10-11：60 Step 越限即终止）。 */
        public boolean stepLimitReached(Consumption consumption) {
            return consumption.steps() >= maxSteps;
        }

        /** LLM 调用闸（40 次）。 */
        public boolean llmLimitReached(Consumption consumption) {
            return consumption.llmCalls() >= maxLlmCalls;
        }

        /** 重规划闸（3 轮；越限 ⇒ failed，§4.2）。 */
        public boolean replanLimitReached(Consumption consumption) {
            return consumption.replanRounds() >= maxReplanRounds;
        }

        /** 补槽闸（3 轮）。 */
        public boolean refillLimitReached(Consumption consumption) {
            return consumption.refillRounds() >= maxRefillRounds;
        }
    }

    private static int intOr(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    /** 供状态机/编排层复用的终态判定快捷（避免各处散落 isTerminal 调用）。 */
    public static boolean terminal(String status) {
        return RunStatus.isValid(status) && RunStatus.valueOf(status.toUpperCase()).isTerminal();
    }
}
