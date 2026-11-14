package com.chinacreator.gzcm.engine.kb.event;

import com.chinacreator.gzcm.common.event.OntologyPublishedEvent;
import com.chinacreator.gzcm.engine.kb.dao.KbOntologySnapshotDao;
import com.chinacreator.gzcm.engine.kb.service.KgMapperService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D71 门禁：schema 拉不到时**禁落 placeholder hash**。
 *
 * <p>原实现的缺陷不是"记为失败"，而是"记为成功"：拉取异常时返回
 * {@code sha256("placeholder-<ontologyId>-<version>")} 并照常 upsert，库里于是留下一个
 * 与本体内容无关的假摘要。真摘要（拉得到的那个 JVM 算出的）与 placeholder 会互相覆盖
 * 同一行（{@code UNIQUE(ontology_id, snap_version, is_deleted)} 上的 upsert），
 * 最终值取决于哪个 JVM 最后写——同一版本号因此可能对应两种 hash，下游拿它判
 * "本体是否变了"的依据彻底失真。</p>
 *
 * <p>修法：拉取/序列化失败一律抛 {@code SchemaFetchException}，落进 {@code runSync} 的
 * 既有 catch ⇒ 写 {@code kg_sync_log} FAILED 行。本测试钉住三条：
 * ①拉取失败 ⇒ 不 upsert、不软标历史版本、写 FAILED 行；
 * ②正向对照：拉取成功 ⇒ 真 hash 落库（防"一律不写"的假绿）；
 * ③占位串形态在整个类里零出现（防有人把 placeholder 换个写法加回来）。</p>
 */
class OntologySnapshotNoPlaceholderHashTest {

    private static final String ONTOLOGY_ID = "ont_probe";
    private static final String VERSION = "9.9.9";
    private static final String BUSZHI_BASE = "http://localhost:18083/api/v1/ecos";

    private final KbOntologySnapshotDao dao = mock(KbOntologySnapshotDao.class);
    private final KgMapperService kgMapper = mock(KgMapperService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private EcosOntologyEventConsumer consumer() {
        return new EcosOntologyEventConsumer(dao, kgMapper, restTemplate, BUSZHI_BASE);
    }

    @Test
    @DisplayName("schema 拉取失败：不写快照、不软标历史、不触发 KG 同步，改写 kg_sync_log FAILED")
    void fetchFailureWritesFailedLogInsteadOfPlaceholderSnapshot() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("403 Forbidden"));

        consumer().runSync(OntologyPublishedEvent.of(
                ONTOLOGY_ID, VERSION, List.of("E1"), List.of("R1"), "tester"));

        verify(dao, never()).upsertSnapshot(anyString(), anyString(), any(), any(), any(), any());
        verify(dao, never()).softMarkPreviousVersion(anyString(), anyString());
        verifyNoInteractions(kgMapper);
        verify(dao, times(1)).insertSyncLogFailed(eq(ONTOLOGY_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("正向对照：schema 拉取成功时真 hash 落库（防「一律不写」假绿）")
    void fetchSuccessPersistsRealHash() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("data", Map.of("entities", List.of("E1"))));

        consumer().runSync(OntologyPublishedEvent.of(
                ONTOLOGY_ID, VERSION, List.of("E1"), List.of("R1"), "tester"));

        verify(dao, times(1)).upsertSnapshot(
                eq(ONTOLOGY_ID), eq(VERSION), anyString(), anyString(), anyString(), anyString());
        verify(dao, never()).insertSyncLogFailed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("空响应体等同拉取失败：不落快照")
    void nullResponseAlsoFails() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        consumer().runSync(OntologyPublishedEvent.of(
                ONTOLOGY_ID, VERSION, List.of(), List.of(), "tester"));

        verify(dao, never()).upsertSnapshot(anyString(), anyString(), any(), any(), any(), any());
        verify(dao, times(1)).insertSyncLogFailed(eq(ONTOLOGY_ID), anyString(), anyString());
    }
}
