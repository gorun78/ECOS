package com.chinacreator.gzcm.engine.ontology.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitResultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitSaveDTO;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitSyncResult;

/**
 * 本体 Git 控制器 → 服务强类型契约的 wire 保真门（VG-2 收口；#147BI）。
 *
 * <p>删掉 Map 垫片后，控制器直接组装 {@link OntologyGitArchiveRequest} / 消费
 * {@link OntologyGitSyncResult}。本门钉三件事：① commit 把 DTO 的仓库/版本/内容/消息原样送进
 * 归档请求，并把结果里的 {@code commitId/tag} 逐字映射到 VO（wire 不缩水）；
 * ② pull 只传 {@code repositoryId}；③ <b>load 收到客户端 {@code url} 也不传给服务</b>
 * （P0 红线：仓库只由服务端注册表定位）——这一点靠「服务方法签名里根本没有 url 参数」+
 * 本门实参捕获双重保证。</p>
 *
 * @author ECOS ontology #147BI
 */
class OntologyGitControllerTest {

    private static OntologyGitResultVO vo(com.chinacreator.gzcm.common.base.ApiResponse<OntologyGitResultVO> resp) {
        assertNotNull(resp.getData(), "响应 data 不得为空");
        return resp.getData();
    }

    @Test
    @DisplayName("commit：DTO → archiveVersion 请求逐字段透传；结果 commitId/tag 映射进 VO")
    void commitMapsDtoToArchiveRequestAndBack() {
        OntologyGitService service = mock(OntologyGitService.class);
        OntologyGitArchiveResult archived = new OntologyGitArchiveResult();
        archived.setStatus(OntologyGitArchiveResult.STATUS_COMMITTED);
        archived.setRepositoryId("ontology-model");
        archived.setCommitId("beef0001");
        archived.setTag("ontology/ont-1/2.1.0");
        archived.setAssetType("ontology");
        archived.setVersionNo("2.1.0");
        archived.setTimestamp(1_700_000_000_000L);
        when(service.archiveVersion(any(com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest.class)))
                .thenReturn(archived);

        OntologyGitSaveDTO dto = new OntologyGitSaveDTO();
        dto.setRepositoryId("ontology-model");
        dto.setAssetType("ontology");
        dto.setVersionNo("2.1.0");
        dto.setContent("{\"b\":2}");
        dto.setMessage("snapshot");

        OntologyGitResultVO out = vo(new OntologyGitController(service).commit("ont-1", dto));

        ArgumentCaptor<com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest> cap =
                ArgumentCaptor.forClass(com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest.class);
        verify(service).archiveVersion(cap.capture());
        OntologyGitArchiveRequest req = cap.getValue();
        assertEquals("ontology-model", req.getRepositoryId());
        assertEquals("ont-1", req.getAssetId());
        assertEquals("2.1.0", req.getVersionNo());
        assertEquals("{\"b\":2}", req.getContent());
        assertEquals("snapshot", req.getCommitMessage());

        assertEquals("committed", out.getStatus());
        assertEquals("beef0001", out.getCommitId(), "wire 不得丢 commitId");
        assertEquals("ontology/ont-1/2.1.0", out.getTag(), "wire 不得丢 tag");
        assertEquals("ont-1", out.getOntologyId());
        assertEquals(1_700_000_000_000L, out.getTimestamp());
    }

    @Test
    @DisplayName("pull：只把 repositoryId 交给服务")
    void pullPassesOnlyRepositoryId() {
        OntologyGitService service = mock(OntologyGitService.class);
        OntologyGitSyncResult synced = new OntologyGitSyncResult();
        synced.setStatus(OntologyGitSyncResult.STATUS_PULLED);
        synced.setOntologyId("ont-2");
        synced.setRepositoryId("ontology-model");
        synced.setRemote("origin");
        when(service.pullVersion(eq("ont-2"), eq("ontology-model"))).thenReturn(synced);

        OntologyGitSaveDTO dto = new OntologyGitSaveDTO();
        dto.setRepositoryId("ontology-model");
        dto.setUrl("http://evil.internal/repo.git");

        OntologyGitResultVO out = vo(new OntologyGitController(service).pull("ont-2", dto));

        verify(service).pullVersion("ont-2", "ontology-model");
        assertEquals("pulled", out.getStatus());
        assertEquals("ontology-model", out.getRepositoryId());
        assertEquals("origin", out.getRemote());
    }

    @Test
    @DisplayName("load：客户端 url 被丢弃（服务只收到 repositoryId）")
    void loadIgnoresClientUrl() {
        OntologyGitService service = mock(OntologyGitService.class);
        OntologyGitSyncResult notAvailable = new OntologyGitSyncResult();
        notAvailable.setStatus(OntologyGitSyncResult.STATUS_NOT_AVAILABLE);
        notAvailable.setReason("load 仅接受服务端 repositoryId");
        when(service.loadVersion(any())).thenReturn(notAvailable);

        OntologyGitSaveDTO dto = new OntologyGitSaveDTO();
        dto.setUrl("http://evil.internal/repo.git");

        OntologyGitResultVO out = vo(new OntologyGitController(service).load(dto));

        // url 从未进入服务调用：签名里没有该参数，这里再钉一次实参
        verify(service).loadVersion(null);
        assertEquals("not_available", out.getStatus());
        assertNull(out.getRepositoryId());
        assertNotNull(out.getReason());
    }
}
