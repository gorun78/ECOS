package com.chinacreator.gzcm.engine.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.security.dao.AuditDao;

/**
 * 审计消费端的主键兜底门（#147BD）。
 *
 * <p><b>为什么</b>：消费端原先「缺 {@code id} 即整条丢弃」。全仓多处 AUDIT 发布器只放
 * {@code eventId}（kb #147BB、workspace 的 `WorkspaceSecurityEngineClient#audit` 等），
 * 这些域的审计证据从未落库且多年无人察觉——丢证据是这条链上代价最高的失败。</p>
 *
 * <p>现口径：<b>有 eventId 就派生确定性主键</b>（FNV-1a，前缀 9；同 eventId ⇒ 同 id ⇒ 重放幂等），
 * 只有 id 与 eventId 双缺才丢弃。钉四条：派生可用、派生物幂等、双缺才丢、显式 id 不被改写。</p>
 *
 * @author ECOS security #147BD
 */
class AuditConsumerIdDerivationTest {

    /** 记录落库主键的 DAO 替身。 */
    private static final class RecordingDao extends AuditDao {
        final List<String> ids = new ArrayList<>();
        final List<String> actions = new ArrayList<>();

        @Override
        public int insertAuditLogIdempotent(String id, String userId, String username, String action,
                                            String resource, String result, String detail, String ip) {
            ids.add(id);
            actions.add(action);
            return 1;
        }

        @Override
        public int fillGovernanceColumns(String id, Map<String, Object> cols) {
            return 0;
        }
    }

    private static Map<String, Object> event(Map<String, Object> extra) {
        Map<String, Object> evt = new LinkedHashMap<>(extra);
        evt.put("action", "rule.version.publish");
        evt.put("resource", "kb-rule-version");
        return evt;
    }

    @Test
    @DisplayName("缺 id 但有 eventId → 派生确定性主键并落库（不再丢弃）")
    void derivesIdWhenOnlyEventIdPresent() {
        RecordingDao dao = new RecordingDao();
        new AuditKafkaConsumer(dao, null).onAuditEvent(event(Map.of("eventId", "evt-147bd-1")));

        assertEquals(1, dao.ids.size(), "有 eventId 的事件必须落库，不得再被丢弃");
        String id = dao.ids.get(0);
        assertTrue(id.startsWith("9") && id.length() == 18,
                "派生物应为 9 前缀 18 位数字（与毫秒型 id 不相撞）: " + id);
    }

    @Test
    @DisplayName("同一 eventId 派生同一 id（重放幂等）")
    void derivationIsDeterministic() {
        RecordingDao dao = new RecordingDao();
        AuditKafkaConsumer consumer = new AuditKafkaConsumer(dao, null);
        consumer.onAuditEvent(event(Map.of("eventId", "evt-147bd-2")));
        consumer.onAuditEvent(event(Map.of("eventId", "evt-147bd-2")));

        assertEquals(2, dao.ids.size());
        assertEquals(dao.ids.get(0), dao.ids.get(1), "同 eventId 必须得到同 id，否则重放会重复记账");
    }

    @Test
    @DisplayName("id 与 eventId 双缺 → 仍丢弃（无法幂等）")
    void dropsOnlyWhenBothMissing() {
        RecordingDao dao = new RecordingDao();
        new AuditKafkaConsumer(dao, null).onAuditEvent(event(Map.of()));

        assertEquals(0, dao.ids.size(), "无任何主键线索的事件不得落库（无法判重）");
    }

    @Test
    @DisplayName("显式 id 原样使用，不被派生逻辑改写")
    void explicitIdWins() {
        RecordingDao dao = new RecordingDao();
        new AuditKafkaConsumer(dao, null).onAuditEvent(event(Map.of("id", "1793778000000123456",
                "eventId", "evt-147bd-3")));

        assertEquals(List.of("1793778000000123456"), dao.ids);
    }
}
