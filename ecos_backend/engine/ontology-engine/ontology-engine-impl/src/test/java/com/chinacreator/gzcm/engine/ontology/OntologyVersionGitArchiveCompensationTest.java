package com.chinacreator.gzcm.engine.ontology;

import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.repository.GitArchivePendingRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * W83/C66 验收 — 版本 × Git 归档可补偿（F03-15）：
 *
 * <ol>
 *   <li>Git 返回 {@code committed} → 版本行回填 {@code git_ref}（真 commitId，禁伪成功）；<b>不入</b>补偿队列；</li>
 *   <li>Git 返回 {@code not_available} → <b>不入 git_ref</b> 反写（禁伪成功）+ <b>入</b>补偿队列（V170.2 表写入 pending）；</li>
 *   <li>Git 抛异常 → 同 ②（补偿队列而非静默丢）；</li>
 *   <li> publié 已经在 main transaction 里 commit —— 补偿队列入队异常不允许反噬发布（不抛）。</li>
 * </ol>
 *
 * <p>全部走 Mockito，不启 Spring 上下文。
 */
class OntologyVersionGitArchiveCompensationTest {

    private OntologyVersionRepository versionRepo;
    private OntologyRepository ontRepo;
    private OntologyProposalService proposal;
    private ApplicationEventPublisher pub;
    private OntologyGitService gitService;
    private GitArchivePendingRepository pendingRepo;
    private OntologyVersionService svc;

    @BeforeEach
    void setUp() {
        versionRepo = mock(OntologyVersionRepository.class);
        ontRepo = mock(OntologyRepository.class);
        proposal = mock(OntologyProposalService.class);
        pub = mock(ApplicationEventPublisher.class);
        gitService = mock(OntologyGitService.class);
        pendingRepo = mock(GitArchivePendingRepository.class);
        svc = new OntologyVersionService(
                versionRepo, ontRepo, proposal, pub,
                Optional.<EventBusService>empty(),
                Optional.of(gitService),
                Optional.of(pendingRepo));
    }

    private static OntologyVersion version(String id, String onto, String ver) {
        OntologyVersion v = new OntologyVersion();
        v.setId(id);
        v.setOntologyId(onto);
        v.setVersionNo(ver);
        v.setStatus("Draft");
        v.setSnapshot("{snapshot:'" + ver + "'}");
        v.setPublisher("alice");
        return v;
    }

    @Test
    void committed_doesNotEnqueueCompensation() {
        OntologyGitArchiveResult ok = new OntologyGitArchiveResult();
        ok.setStatus(OntologyGitArchiveResult.STATUS_COMMITTED);
        ok.setCommitId("abc123");
        when(gitService.archiveVersion(any(OntologyGitArchiveRequest.class))).thenReturn(ok);

        OntologyVersion v = version("ver1", "onto-1", "1.0.0");
        when(versionRepo.findById("ver1")).thenReturn(java.util.Optional.of(v));
        when(versionRepo.updateStatus("ver1", "Published")).thenReturn(1);

        svc.publishVersionById("ver1");

        // git_ref 回填（commitId 真值，禁 fake）
        verify(versionRepo, times(1)).setGitRef("ver1", "abc123");
        // 归档成功 —— 补偿入口零接触
        verifyNoInteractions(pendingRepo);
    }

    @Test
    void notAvailable_enqueuesPendingWithoutFakeRef() {
        OntologyGitArchiveResult na = new OntologyGitArchiveResult();
        na.setStatus(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE);
        na.setReason("git repo=ontology not registered");
        when(gitService.archiveVersion(any(OntologyGitArchiveRequest.class))).thenReturn(na);
        when(pendingRepo.enqueue(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("q-1");

        OntologyVersion v = version("ver1", "onto-1", "1.0.1");
        when(versionRepo.findById("ver1")).thenReturn(java.util.Optional.of(v));
        when(versionRepo.updateStatus("ver1", "Published")).thenReturn(1);

        svc.publishVersionById("ver1");

        // 发布成功不受影响（publish 主流程被 fan-out / enqueue 都不应抛）
        // 未回填 git_ref（禁伪成功：queue 有 pending 时版本状态仍 Published 但不显示 git_ref）
        verify(versionRepo, never()).setGitRef(anyString(), anyString());
        // 入队一次，字段对齐（payload = 版本 snapshot 原文；commit message 含 onto+ver；author=publisher）
        verify(pendingRepo, times(1)).enqueue(
            eq("onto-1"), eq("ver1"), eq("1.0.1"),
            eq("ontology"),
            org.mockito.ArgumentMatchers.contains("onto-1"),
            eq("alice"),
            eq("{snapshot:'1.0.1'}"),
            any());
    }

    @Test
    void gitThrows_enqueuesPendingAndNeverRollsBack() {
        when(gitService.archiveVersion(any(OntologyGitArchiveRequest.class)))
            .thenThrow(new IllegalStateException("git lock unavailable"));
        when(pendingRepo.enqueue(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("q-2");

        OntologyVersion v = version("ver1", "onto-1", "1.0.2");
        when(versionRepo.findById("ver1")).thenReturn(java.util.Optional.of(v));
        when(versionRepo.updateStatus("ver1", "Published")).thenReturn(1);

        // 发布不抛（归档失败 ≠ 发布失败；补偿兜底可重试）
        svc.publishVersionById("ver1");

        verify(pendingRepo, times(1)).enqueue(
            eq("onto-1"), eq("ver1"), eq("1.0.2"),
            eq("ontology"),
            org.mockito.ArgumentMatchers.contains("onto-1"),
            eq("alice"),
            eq("{snapshot:'1.0.2'}"),
            any());
        verify(versionRepo, never()).setGitRef(anyString(), anyString());
    }

    @Test
    void enqueueFailure_doesNotPropagateToPublish() {
        OntologyGitArchiveResult na = new OntologyGitArchiveResult();
        na.setStatus(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE);
        na.setReason("unreachable");
        when(gitService.archiveVersion(any(OntologyGitArchiveRequest.class))).thenReturn(na);
        when(pendingRepo.enqueue(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        OntologyVersion v = version("ver1", "onto-1", "1.0.3");
        when(versionRepo.findById("ver1")).thenReturn(java.util.Optional.of(v));
        when(versionRepo.updateStatus("ver1", "Published")).thenReturn(1);

        // 补偿队列自身写入失败 <b>不能</b> 反噬发布（可用性优先；告警由 01 册 runtime-monitor 通道，见 W83/C66 表述）
        // 反例：若允许抛，断言用 assertThrows 会命中 IllegalStateException 抛给 publish 调用方（无效）
        svc.publishVersionById("ver1");
        // 无 setGitRef（未 commit），发布已被 fanOut 完成 —— 此处判定以 verify 全符：主流程 3 步 touch
        verify(versionRepo, never()).setGitRef(anyString(), anyString());
    }
}
