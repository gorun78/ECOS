package com.chinacreator.gzcm.engine.cognitive2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.eventbus.audit.GovernanceAuditPublisher;

/**
 * cognitive 腿治理审计门（#147BV · §2.9.9 G5「其余发布域的 {@code git_commit_sha}」）。
 *
 * <p><b>钉的是什么</b>：{@code ModelRegistryService#archivePublishedModel} 在归档成功
 * （{@code ARCHIVED}）后必须发一条携 {@code git_repository=cognitive-model} /
 * {@code git_commit_sha} / {@code content_hash} 的治理审计；{@code content_hash} 优先取行内
 * 已冻结的权威值（门禁比对的那一枚），缺位才退回 JSON 摘要。归档未成功不得发。</p>
 *
 * <p><b>变异自证</b>：把审计调用挪到 {@code status} 判断之前 ⇒ {@code notArchivedSendsNothing} 红；
 * 把行内 {@code content_hash} 读法改成「一律 sha256(json)」⇒ {@code frozenHashIsPreferred} 红。</p>
 */
class ModelArchiveAuditTest {

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

    private static ObjectProvider<EventBusService> provider(EventBusService bus) {
        return new ObjectProvider<>() {
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
    }

    private static ModelRegistryService service(ArchiveOutcome outcome, CapturingBus bus) throws Exception {
        DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
        when(archiver.archive(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(outcome);

        ObjectProvider<DesignAssetArchiver> archiverProvider = new ObjectProvider<>() {
            @Override
            public DesignAssetArchiver getObject() {
                return archiver;
            }

            @Override
            public DesignAssetArchiver getObject(Object... args) {
                return archiver;
            }

            @Override
            public DesignAssetArchiver getIfAvailable() {
                return archiver;
            }

            @Override
            public DesignAssetArchiver getIfUnique() {
                return archiver;
            }
        };

        ModelRegistryService service = new ModelRegistryService(
                mock(ModelRegistryDao.class), mock(ModelPublishGateEvaluator.class), archiverProvider);
        Field f = ModelRegistryService.class.getDeclaredField("governanceAuditPublisher");
        f.setAccessible(true);
        f.set(service, bus == null ? null : new GovernanceAuditPublisher(provider(bus)));
        return service;
    }

    private static void archive(ModelRegistryService service, Map<String, Object> row) throws Exception {
        Method m = ModelRegistryService.class.getDeclaredMethod(
                "archivePublishedModel", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "corm_row_1", "publisher-1", row);
    }

    @Test
    @DisplayName("归档成功 ⇒ 治理审计携 cognitive-model + 真实提交号 + 行内冻结 content_hash")
    void archivedEmitsGovernanceAuditWithFrozenHash() throws Exception {
        CapturingBus bus = new CapturingBus();
        ModelRegistryService service = service(
                new ArchiveOutcome(ArchiveOutcome.Status.ARCHIVED, "cafe123", "v2", null), bus);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("row_id", "corm_row_1");
        row.put("version_no", "2");
        row.put("content_hash", "frozen-hash-abc");
        archive(service, row);

        assertEquals(1, bus.payloads.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) bus.payloads.get(0);
        assertEquals("model.archive", event.get("action"));
        assertEquals("cognitive-model", event.get("resource"));
        assertEquals("SUCCESS", event.get("result"));
        assertEquals("cognitive", event.get("domain"));
        assertEquals("cognitive_model", event.get("object_type"));
        assertEquals("corm_row_1", event.get("object_id"));
        assertEquals("publisher-1", event.get("actor_id"));
        assertEquals("cognitive-model", event.get("git_repository"));
        assertEquals("cafe123", event.get("git_commit_sha"));
        assertEquals("frozen-hash-abc", event.get("content_hash"),
                "content_hash 须取行内已冻结的权威值（与发布门禁比对的是同一枚）");
        assertNotNull(event.get("id"), "消费端以 id 判重，缺则整条丢弃");
    }

    @Test
    @DisplayName("行内无冻结哈希 ⇒ 退回 JSON 摘要（不写空哈希）")
    void fallsBackToJsonDigestWhenFrozenHashMissing() throws Exception {
        CapturingBus bus = new CapturingBus();
        ModelRegistryService service = service(
                new ArchiveOutcome(ArchiveOutcome.Status.ARCHIVED, "cafe124", null, null), bus);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", "1");
        archive(service, row);

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) bus.payloads.get(0);
        String hash = String.valueOf(event.get("content_hash"));
        org.junit.jupiter.api.Assertions.assertTrue(hash.matches("[0-9a-f]{64}"), "兜底哈希须为 sha256，实际=" + hash);
    }

    @Test
    @DisplayName("归档未成功（FAILED）⇒ 不发审计")
    void notArchivedSendsNothing() throws Exception {
        CapturingBus bus = new CapturingBus();
        ModelRegistryService service = service(
                new ArchiveOutcome(ArchiveOutcome.Status.FAILED, null, null, "io error"), bus);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", "3");
        archive(service, row);

        assertEquals(0, bus.payloads.size());
    }

    @Test
    @DisplayName("发布器未装配 ⇒ 归档腿不抛（发布结果不被审计通道翻转）")
    void missingPublisherDoesNotThrow() throws Exception {
        ModelRegistryService service = service(
                new ArchiveOutcome(ArchiveOutcome.Status.ARCHIVED, "cafe125", null, null), null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", "4");
        archive(service, row);
    }
}
