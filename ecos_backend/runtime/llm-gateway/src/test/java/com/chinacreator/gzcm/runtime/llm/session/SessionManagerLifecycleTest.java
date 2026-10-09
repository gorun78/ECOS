package com.chinacreator.gzcm.runtime.llm.session;

import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-10 验收（文档冻结的可执行形态）：
 *
 * <ol>
 *   <li>{@code closedSessionIsEvictedFromMemory} —— 关闭即摘除（X-34 的"只置 closed 不 remove"已废）；</li>
 *   <li>{@code idleSessionsExpireWithinConfiguredTtl} —— 空闲条目在<b>配置 TTL</b> 内被清扫回收，
 *       不依赖再被读（懒淘汰回收不到"不再被读"的条目，内存门禁就不可判）。</li>
 * </ol>
 *
 * <p>外加容量上界用例：F06-10 的"淘汰结构"是 TTL <b>＋</b> 上限两件，缺一不可判。</p>
 *
 * <p>本类不启 Spring 上下文，直接走 {@link SessionManagerImpl} 的显式数值装配点，
 * 以便把 TTL/上限取到秒级与个位数（生产默认 1800s/2000 见 {@code llm.sessions}）。</p>
 */
@DisplayName("F06-10 会话内存视图有界化")
class SessionManagerLifecycleTest {

    @Test
    @DisplayName("要点2/X-34: closeSession 必须把会话摘出视图并释放引用")
    void closedSessionIsEvictedFromMemory() {
        SessionManagerImpl manager = new SessionManagerImpl(1800L, 100);

        String sessionId = manager.createSession("aiming", "default", "sys");
        assertNotNull(manager.getSession(sessionId), "新建会话应可取回");
        assertEquals(1, manager.residentEntryCount());

        manager.closeSession(sessionId);

        assertEquals(0, manager.residentEntryCount(), "关闭后视图驻留数必须归零，不能只置状态");
        assertNull(manager.getSession(sessionId), "关闭后不得再从视图取回该会话对象");
        assertTrue(manager.getActiveSessions(null).isEmpty(), "活跃列表也不得再含已关闭会话");
    }

    @Test
    @DisplayName("要点1/X-34: 空闲超过配置 TTL ⇒ 清扫任务回收（不靠再被读）")
    void idleSessionsExpireWithinConfiguredTtl() {
        SessionManagerImpl manager = new SessionManagerImpl(1L, 100);
        String idle = manager.createSession("aiming", "default", "sys");
        String busy = manager.createSession("aiming", "default", "sys");

        // 两个都 aging；只让 busy 保持新近访问（expireAfterAccess 语义：访问重置计时）
        manager.getSession(busy);
        LocalDateTime past = LocalDateTime.now().minusSeconds(30);
        manager.getSession(idle).setLastActivityAt(past);

        int reclaimed = manager.sweepIdleEntries();

        assertEquals(1, reclaimed, "只有空闲超 TTL 的那条被回收");
        assertEquals(1, manager.residentEntryCount());
        assertNull(manager.getSession(idle), "空闲超时会话必须已不在视图中");
        assertNotNull(manager.getSession(busy), "近期有访问的会话不受影响");
    }

    @Test
    @DisplayName("要点1: 驻留不超配置上限，超限按最久未访问淘汰")
    void residentSessionsNeverExceedConfiguredMax() {
        SessionManagerImpl manager = new SessionManagerImpl(1800L, 3);
        String s1 = manager.createSession("aiming", "p", "sys");
        String s2 = manager.createSession("aiming", "p", "sys");
        String s3 = manager.createSession("aiming", "p", "sys");
        manager.getSession(s1); // s1 变为最近访问 ⇒ 淘汰候选落到 s2

        String s4 = manager.createSession("aiming", "p", "sys");

        assertTrue(manager.residentEntryCount() <= 3,
                "驻留数不得超上限，实际=" + manager.residentEntryCount());
        assertEquals(3, manager.residentEntryCount());
        assertNull(manager.getSession(s2), "应淘汰最久未访问条目");
        assertNotNull(manager.getSession(s1));
        assertNotNull(manager.getSession(s3));
        assertNotNull(manager.getSession(s4));
    }

    @Test
    @DisplayName("配置面：非正值禁解释为'永不过期/无上限'；属性 bean 缺席回落缺省口径")
    void boundsNeverDegradetoUnbounded() {
        SessionManagerImpl illegal = new SessionManagerImpl(0L, -5);
        assertEquals(SessionManagerImpl.DEFAULT_TTL_SECONDS, illegal.idleTtlSeconds(),
                "非正 TTL 必须回落 30 分钟，禁变成事实上的无界驻留");
        assertEquals(SessionManagerImpl.DEFAULT_MAX_SESSIONS, illegal.maxResidentEntries(),
                "非正上限必须回落 2000");

        SessionManagerImpl noProps = new SessionManagerImpl(propertiesProvider(null));
        assertEquals(SessionManagerImpl.DEFAULT_TTL_SECONDS, noProps.idleTtlSeconds());
        assertEquals(SessionManagerImpl.DEFAULT_MAX_SESSIONS, noProps.maxResidentEntries());

        LLMGatewayProperties props = new LLMGatewayProperties();
        props.getSessions().setTtlSeconds(45L);
        props.getSessions().setMaxActiveSessions(7);
        SessionManagerImpl configured = new SessionManagerImpl(propertiesProvider(props));
        assertEquals(45L, configured.idleTtlSeconds(), "yml 配置面必须真正进入视图参数（llm.sessions.*）");
        assertEquals(7, configured.maxResidentEntries());
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<LLMGatewayProperties>
            propertiesProvider(LLMGatewayProperties props) {
        org.springframework.beans.factory.ObjectProvider<LLMGatewayProperties> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(props);
        return provider;
    }
}
