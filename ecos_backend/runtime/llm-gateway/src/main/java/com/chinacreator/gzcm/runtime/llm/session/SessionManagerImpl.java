package com.chinacreator.gzcm.runtime.llm.session;

import com.chinacreator.gzcm.common.service.IMemorySweepable;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SessionManager 实现 — <b>有界</b>进程内会话视图（详细设计-06 F06-10 / W152 / X-34）。
 *
 * <p>旧实现是「{@code ConcurrentHashMap} 无上限 + {@code closeSession} 只置状态不摘除」：
 * 会话条目既不会因关闭释放、也不会因空闲回收 ⇒ RSS 单调增长，F06-09 的内存门禁无从判定。
 * 现在三条都有确定语义：</p>
 * <ol>
 *   <li><b>expireAfterAccess</b>：读/写会话都重置空闲计时；空闲超过
 *       {@code llm.sessions.ttl-seconds}（默认 30 分钟）即回收；</li>
 *   <li><b>容量上限</b>：驻留条目数不超过 {@code llm.sessions.max-active-sessions}，
 *       超限按<b>最久未访问</b>淘汰并 WARN（禁静默丢会话）；</li>
 *   <li><b>关闭即摘除</b>：{@link #closeSession} 从视图 remove，会话对象（含 systemPrompt 等
 *       强引用）随即可被 GC 回收。</li>
 * </ol>
 *
 * <p><b>淘汰由谁驱动</b>：懒淘汰（只在读路径判超时）永远回收不到"已超时且不再被读"的条目，
 * 故本类实现 {@link IMemorySweepable} 把清扫能力交出去，周期调用方是 runtime-task 的
 * {@code SessionMemorySweepTask}（铁律 §2.5-4：本模块禁自建 {@code @Scheduled}/线程池，X-25）。</p>
 *
 * <p><b>并发模型</b>：视图不是吞吐热路径（每次 Agent 调用 1~2 次访问），单锁换取淘汰顺序的
 * 确定性 —— {@code accessOrder=true} 的 {@link LinkedHashMap} 迭代序即访问序，
 * 与 {@code ConcurrentHashMap} 的哈希迭代序不同，不存在"淘汰谁"的竞态。</p>
 *
 * <p>Hermes 持久化适配器（{@code ecosHermesSessionAdapter}）仍以反射按名注入，
 * 规避 llm-gateway → agent-service 的编译期循环依赖；持久层存活与内存视图有界互不影响。</p>
 */
@Service
public class SessionManagerImpl implements SessionManager, IMemorySweepable {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);

    /** 缺省空闲存活时长（秒）；权威值 = {@code llm.sessions.ttl-seconds} */
    static final long DEFAULT_TTL_SECONDS = 1800L;

    /** 缺省驻留上限；权威值 = {@code llm.sessions.max-active-sessions} */
    static final int DEFAULT_MAX_SESSIONS = 2000;

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_CLOSED = "closed";
    private static final String STATUS_TIMEDOUT = "timedout";

    /** 会话存储：sessionId → AgentSession；访问序链，头部即最久未访问（淘汰候选） */
    private final LinkedHashMap<String, AgentSession> sessions = new LinkedHashMap<>(64, 0.75f, true);

    /** 与 sessions 配套的访问锁（读/写/清扫/淘汰同一把，保证判定确定性） */
    private final Object viewLock = new Object();

    private final long ttlSeconds;
    private final int maxSessions;

    /**
     * Hermes 会话持久化适配器 (optional — agent-service 未引入时为 null)。
     * 通过 @Qualifier 按名称注入，类型为 Object 以规避编译期循环依赖；
     * 运行时通过反射调用 createSession / searchSessions / isAvailable。
     */
    @Autowired(required = false)
    @Qualifier("ecosHermesSessionAdapter")
    private Object ecosHermesSessionAdapter;

    @Autowired
    public SessionManagerImpl(ObjectProvider<LLMGatewayProperties> propertiesProvider) {
        this(rawTtl(propertiesProvider.getIfAvailable()),
                rawMax(propertiesProvider.getIfAvailable()));
    }

    /**
     * 显式数值装配点（测试与非 Spring 场景；生产走上面的属性解析链）。
     *
     * <p>非正值在这里统一回落缺省，<b>两个</b>装配入口共用同一处判定 —— 否则测试用的窄口径
     * 与生产用的宽口径会给出不同的有界性保证。</p>
     */
    SessionManagerImpl(long ttlSeconds, int maxSessions) {
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : defaultTtl(ttlSeconds);
        this.maxSessions = maxSessions > 0 ? maxSessions : defaultMax(maxSessions);
        log.info("[F06-10] 会话内存视图已设界: ttlSeconds={} maxActiveSessions={}",
                this.ttlSeconds, this.maxSessions);
    }

    private static long rawTtl(LLMGatewayProperties props) {
        return props == null ? DEFAULT_TTL_SECONDS : props.getSessions().getTtlSeconds();
    }

    private static int rawMax(LLMGatewayProperties props) {
        return props == null ? DEFAULT_MAX_SESSIONS : props.getSessions().getMaxActiveSessions();
    }

    private static long defaultTtl(long configured) {
        // 0 或负值绝不解释成"永不过期"——那正是 X-34 的无界增长形态
        log.error("[F06-10] ttlSeconds 非正 ⇒ 回落缺省（禁无界驻留）: value={} default={}",
                configured, DEFAULT_TTL_SECONDS);
        return DEFAULT_TTL_SECONDS;
    }

    private static int defaultMax(int configured) {
        log.error("[F06-10] maxActiveSessions 非正 ⇒ 回落缺省（禁无界驻留）: value={} default={}",
                configured, DEFAULT_MAX_SESSIONS);
        return DEFAULT_MAX_SESSIONS;
    }

    // ---- Hermes adapter 反射工具方法 ----

    /** 反射调用 adapter.isAvailable() */
    private boolean isHermesAvailable() {
        if (ecosHermesSessionAdapter == null) return false;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("isAvailable");
            return (boolean) m.invoke(ecosHermesSessionAdapter);
        } catch (Exception e) {
            log.debug("HermesSessionAdapter.isAvailable() reflection failed: {}", e.getMessage());
            return false;
        }
    }

    /** 反射调用 adapter.createSession(sessionId, title, profile) */
    private void hermesCreateSession(String sessionId, String title, String profile) {
        if (ecosHermesSessionAdapter == null) return;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("createSession", String.class, String.class, String.class);
            m.invoke(ecosHermesSessionAdapter, sessionId, title, profile);
        } catch (Exception e) {
            log.warn("HermesSessionAdapter.createSession() reflection failed: {}", e.getMessage());
        }
    }

    /** 反射调用 adapter.searchSessions(query, limit) */
    private void hermesSearchSessions(String query, int limit) {
        if (ecosHermesSessionAdapter == null) return;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("searchSessions", String.class, int.class);
            m.invoke(ecosHermesSessionAdapter, query, limit);
        } catch (Exception e) {
            log.warn("HermesSessionAdapter.searchSessions() reflection failed: {}", e.getMessage());
        }
    }

    // ---- SessionManager 接口实现 ----

    @Override
    public String createSession(String subsystem, String profileName, String systemPrompt) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        AgentSession session = new AgentSession(sessionId, subsystem, profileName, systemPrompt);
        synchronized (viewLock) {
            sessions.put(sessionId, session);
            enforceCapacityLocked();
        }
        log.info("Session created: id={}, subsystem={}, profile={} (resident={})",
                sessionId, subsystem, profileName, residentEntryCount());

        // 优先持久化到 Hermes，失败降级进程内视图
        if (ecosHermesSessionAdapter != null) {
            try {
                String title = profileName != null ? profileName : "ECOS Session";
                String profile = subsystem != null ? subsystem : "ecos-ai-agent";
                hermesCreateSession(sessionId, title, profile);
                log.debug("Session [{}] persisted to Hermes", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session persistence failed for [{}], falling back to in-memory only: {}",
                        sessionId, e.getMessage());
            }
        }

        return sessionId;
    }

    @Override
    public AgentSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        // 优先尝试 Hermes adapter 查询（验证持久化层是否存活）
        if (isHermesAvailable()) {
            try {
                hermesSearchSessions(sessionId, 1);
                log.debug("Hermes adapter available for session [{}] lookup", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session lookup failed for [{}], falling back to in-memory: {}",
                        sessionId, e.getMessage());
            }
        }

        synchronized (viewLock) {
            AgentSession session = sessions.get(sessionId); // 命中即移到访问序链尾 = expireAfterAccess
            if (session == null) {
                return null;
            }
            if (!STATUS_ACTIVE.equals(session.getStatus())) {
                return session; // 已非活跃（closed/timedout），不改计时
            }
            if (isIdleExpired(session)) {
                sessions.remove(sessionId);
                session.setStatus(STATUS_TIMEDOUT);
                log.warn("[F06-10] 会话空闲超时 ⇒ 标记 timedout 并摘出内存视图: id={} idleSec>={} "
                        + "(last activity: {})", sessionId, ttlSeconds, session.getLastActivityAt());
                return null;
            }
            session.touch();
            return session;
        }
    }

    @Override
    public void closeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        // 优先尝试 Hermes adapter 查询确认
        if (isHermesAvailable()) {
            try {
                hermesSearchSessions(sessionId, 1);
                log.debug("Hermes adapter notified for session [{}] close", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session close notification failed for [{}]: {}", sessionId, e.getMessage());
            }
        }

        AgentSession removed;
        synchronized (viewLock) {
            // X-34：关闭必须摘除 —— 只置 closed 状态会让条目永久驻留（历史形态）
            removed = sessions.remove(sessionId);
        }
        if (removed != null) {
            removed.setStatus(STATUS_CLOSED);
            log.info("[F06-10] Session [{}] closed and evicted from memory view (resident={})",
                    sessionId, residentEntryCount());
        } else {
            log.warn("[F06-10] 关闭未知会话（可能已被 TTL/容量淘汰或从未创建）: id={} resident={}",
                    sessionId, residentEntryCount());
        }
    }

    @Override
    public List<AgentSession> getActiveSessions(String subsystem) {
        List<AgentSession> snapshot = new ArrayList<>();
        synchronized (viewLock) {
            sweepExpiredLocked();
            for (AgentSession s : sessions.values()) {
                if (!STATUS_ACTIVE.equals(s.getStatus())) {
                    continue;
                }
                if (subsystem != null && !subsystem.equals(s.getSubsystem())) {
                    continue;
                }
                snapshot.add(s);
            }
        }
        return snapshot;
    }

    @Override
    public int getActiveSessionCount(String subsystem) {
        return getActiveSessions(subsystem).size();
    }

    // ---- IMemorySweepable：runtime-task 周期清扫入口（F06-10，禁本模块自建调度器）----

    @Override
    public int sweepIdleEntries() {
        synchronized (viewLock) {
            int expired = sweepExpiredLocked();
            int overCapacity = enforceCapacityLocked();
            return expired + overCapacity;
        }
    }

    @Override
    public int residentEntryCount() {
        synchronized (viewLock) {
            return sessions.size();
        }
    }

    /** 驻留上限（供门禁/诊断面核对配置生效，非视图内容）。 */
    public int maxResidentEntries() {
        return maxSessions;
    }

    /** 空闲存活时长配置值（秒）。 */
    public long idleTtlSeconds() {
        return ttlSeconds;
    }

    // ---- 内部淘汰逻辑（调用方必须持 viewLock）----

    private boolean isIdleExpired(AgentSession session) {
        Duration idle = Duration.between(session.getLastActivityAt(), LocalDateTime.now());
        return idle.getSeconds() > ttlSeconds;
    }

    /** 摘除空闲超时条目并标记 timedout。返回摘除条数。 */
    private int sweepExpiredLocked() {
        int removed = 0;
        Iterator<Map.Entry<String, AgentSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            AgentSession session = it.next().getValue();
            if (STATUS_ACTIVE.equals(session.getStatus()) && isIdleExpired(session)) {
                it.remove();
                session.setStatus(STATUS_TIMEDOUT);
                removed++;
                log.warn("[F06-10] 清扫回收空闲会话: id={} idleSec>={}",
                        session.getSessionId(), ttlSeconds);
            }
        }
        return removed;
    }

    /** 超容量时按最久未访问（链头）淘汰。返回淘汰条数。 */
    private int enforceCapacityLocked() {
        int evicted = 0;
        Iterator<Map.Entry<String, AgentSession>> it = sessions.entrySet().iterator();
        while (sessions.size() > maxSessions && it.hasNext()) {
            Map.Entry<String, AgentSession> eldest = it.next();
            it.remove();
            eldest.getValue().setStatus(STATUS_TIMEDOUT);
            evicted++;
            log.warn("[F06-10] 驻留超上限 ⇒ 淘汰最久未访问会话（非用户关闭）: id={} max={}",
                    eldest.getKey(), maxSessions);
        }
        return evicted;
    }
}
