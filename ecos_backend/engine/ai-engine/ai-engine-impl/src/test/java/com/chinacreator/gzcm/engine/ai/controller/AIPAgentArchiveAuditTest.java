package com.chinacreator.gzcm.engine.ai.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.eventbus.audit.GovernanceAuditPublisher;

/**
 * ai 腿治理审计门（#147BV · §2.9.9 G5「其余发布域的 {@code git_commit_sha}」）。
 *
 * <p><b>钉的是什么</b>：{@code AIPAgentController#archiveAgent} 在归档成功（{@code ARCHIVED}）后
 * 必须发一条携 {@code git_repository=agent-collab} / {@code git_commit_sha} / {@code content_hash}
 * 的治理审计；<b>归档未成功不得发</b>——「没归档」被读成「已发布」正是本节要消灭的错账。</p>
 *
 * <p><b>变异自证</b>：把 {@code auditAgentArchive} 的调用移到 {@code status} 判断之前 ⇒
 * {@code notArchivedSendsNothing} 红；删掉 {@code git_commit_sha} 透传 ⇒ 键断言红。</p>
 */
class AIPAgentArchiveAuditTest {

    private static final class CapturingBus implements EventBusService {
        final List<Object> payloads = new ArrayList<>();

        @Override
        public void publish(String topic, Object payload) {
            assertEquals(KafkaTopics.AUDIT, topic);
            payloads.add(payload);
        }

        @Override
        public void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler) {
        }

        @Override
        public boolean isKafkaAvailable() {
            return true;
        }
    }

    /** 只答 updateAgent 用到的两条腿。 */
    private static final class FakeRepo implements AgentRegistryRepository {
        private final AgentRegistryEntity row;

        FakeRepo(AgentRegistryEntity row) {
            this.row = row;
        }

        @Override
        public AgentRegistryEntity findById(String id) {
            return row;
        }

        @Override
        public List<AgentRegistryEntity> findAll() {
            return List.of(row);
        }

        @Override
        public List<AgentRegistryEntity> findActive() {
            return List.of(row);
        }

        @Override
        public int insert(AgentRegistryEntity agent) {
            return 1;
        }

        @Override
        public int update(AgentRegistryEntity agent) {
            return 1;
        }

        @Override
        public int delete(String id) {
            return 1;
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static AIPAgentController controller(ArchiveOutcome outcome, CapturingBus bus) throws Exception {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId("a-1");
        entity.setName("Agent One");
        entity.setRole("aip");
        entity.setStatus("draft");
        entity.setMetadata("{}");

        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(outcome);

        ObjectProvider<EventBusService> provider = new ObjectProvider<>() {
            @Override
            public EventBusService getObject() {
                return bus;
            }

            @Override
            public EventBusService getObject(Object... args) {
                return bus;
            }

            @Override
            public EventBusService getIfAvailable() {
                return bus;
            }

            @Override
            public EventBusService getIfUnique() {
                return bus;
            }
        };

        AIPAgentController controller = new AIPAgentController();
        set(controller, "agentRepo", new FakeRepo(entity));
        set(controller, "designAssetArchiver", archiver);
        set(controller, "governanceAuditPublisher", new GovernanceAuditPublisher(provider));
        return controller;
    }

    @Test
    @DisplayName("归档成功 ⇒ 治理审计携 agent-collab + 真实提交号 + 内容哈希")
    void archivedEmitsGovernanceAudit() throws Exception {
        CapturingBus bus = new CapturingBus();
        AIPAgentController controller = controller(
                new ArchiveOutcome(ArchiveOutcome.Status.ARCHIVED, "abc1234", null, null), bus);

        controller.updateAgent("a-1", Map.of("description", "changed"));

        assertEquals(1, bus.payloads.size(), "归档成功必须且只能发一条治理审计");
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) bus.payloads.get(0);
        assertEquals("agent.archive", event.get("action"));
        assertEquals("ai-agent", event.get("resource"));
        assertEquals("SUCCESS", event.get("result"));
        assertEquals("agent-collab", event.get("git_repository"));
        assertEquals("abc1234", event.get("git_commit_sha"));
        assertEquals("agent", event.get("object_type"));
        assertEquals("a-1", event.get("object_id"));
        assertEquals("APPLIED", event.get("apply_status"));
        assertEquals("PUBLISH_ARCHIVED", event.get("verification_status"));
        assertNotNull(event.get("id"), "消费端以 id 判重，缺则整条丢弃");
        String contentHash = String.valueOf(event.get("content_hash"));
        assertTrue(contentHash.matches("[0-9a-f]{64}"), "content_hash 须为 sha256 十六进制，实际=" + contentHash);
    }

    @Test
    @DisplayName("归档未成功（NOT_AVAILABLE）⇒ 不发审计（不得把没归档报成已发布）")
    void notArchivedSendsNothing() throws Exception {
        CapturingBus bus = new CapturingBus();
        AIPAgentController controller = controller(
                new ArchiveOutcome(ArchiveOutcome.Status.NOT_AVAILABLE, null, null, "no repo"), bus);

        controller.updateAgent("a-1", Map.of("description", "changed"));

        assertEquals(0, bus.payloads.size());
    }

    @Test
    @DisplayName("发布器未装配 ⇒ 业务写入不受影响（不抛）")
    void missingPublisherDoesNotBreakBusiness() throws Exception {
        CapturingBus bus = new CapturingBus();
        AIPAgentController controller = controller(
                new ArchiveOutcome(ArchiveOutcome.Status.ARCHIVED, "abc1234", null, null), bus);
        set(controller, "governanceAuditPublisher", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "changed");
        assertNotNull(controller.updateAgent("a-1", body));
        assertEquals(0, bus.payloads.size());
    }
}
