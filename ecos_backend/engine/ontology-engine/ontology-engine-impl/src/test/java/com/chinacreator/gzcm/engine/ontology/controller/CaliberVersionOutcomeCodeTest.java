package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.OntologyErrorCode;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberFormulaDslService;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 口径版本 404／409 出口族（#146ap）— §6.3.3 在册码 ECOS-ONTO-080／011 的属主出口承载。
 *
 * <p>断言：
 * <ol>
 *   <li>080（本域唯一 404 语义）：detail／current／addVersion 三腿命中 ⇒ 404 + 属主机读码，禁 5xx 掩蔽；</li>
 *   <li>011（版本幂等 409）：显式升版撞 {@code uniq_ecos_caliber_ver} ⇒ DuplicateKeyException 被
 *       端点具名捕获 ⇒ 409 + 属主机读码，不落 500（同版本行不重复产、不产生第二版本行）；</li>
 *   <li>idempotent 语义面：撞唯一键时不产生第二插腿（insertVersion 只发生过一次）。</li>
 * </ol>
 *
 * <p>纯 Mockito 无 Spring 上下文；machine 码取 {@link OntologyErrorCode} 属主常量断言，
 * 字面量不回拼（与 {@code NoMachineCodeFactoryFaceRatchetTest} 的零容差判据同向）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberVersionOutcomeCodeTest {

    private static final String CODE = "CAL_X";

    @Mock
    private CaliberService caliberService;

    @Mock
    private CaliberFormulaDslService dslService;

    private CaliberController controller;

    @BeforeEach
    void setUp() {
        controller = new CaliberController(caliberService, dslService);
    }

    private Map<String, Object> headRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "cid-1");
        row.put("code", CODE);
        return row;
    }

    // ── 080：本域唯一 404 语义 ──────────────────────────────

    @Test
    @DisplayName("detail 404 腿：口径编码不存在 ⇒ 404 + ECOS-ONTO-080（禁 5xx 掩蔽）")
    void detailMissingCodeIs404WithOwnerCode() {
        when(caliberService.findByCode(CODE)).thenReturn(java.util.Optional.empty());

        ApiResponse<com.chinacreator.gzcm.engine.ontology.dto.CaliberVO> resp = controller.detail(CODE);

        assertEquals(404, resp.getCode());
        assertEquals(OntologyErrorCode.ECOS_ONTO_080, resp.getErrorCode());
    }

    @Test
    @DisplayName("current 404 腿：无当前 APPROVED 版本 ⇒ 404 + ECOS-ONTO-080（跨引擎只读面同法）")
    void currentMissingIs404WithOwnerCode() {
        when(caliberService.getByCaliberCodeAndCurrentVersion(CODE))
                .thenReturn(java.util.Optional.empty());

        ApiResponse<?> resp = controller.current(CODE);

        assertEquals(404, resp.getCode());
        assertEquals(OntologyErrorCode.ECOS_ONTO_080, resp.getErrorCode());
    }

    @Test
    @DisplayName("addVersion 404 腿：升版前寻址落空 ⇒ 404 + ECOS-ONTO-080，插腿零调用")
    void addVersionMissingCodeIs404WithoutInsert() {
        when(caliberService.findByCode(CODE)).thenReturn(java.util.Optional.empty());
        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("1");

        ApiResponse<?> resp = controller.addVersion(CODE, dto);

        assertEquals(404, resp.getCode());
        assertEquals(OntologyErrorCode.ECOS_ONTO_080, resp.getErrorCode());
        verify(caliberService, never()).insertVersion(anyString(), anyString(), any(), any(), any(), any());
    }

    // ── 011：版本幂等 409 ───────────────────────────────────

    @Test
    @DisplayName("addVersion 版本幂等腿：显式 version_no 撞唯一约束 ⇒ 409 + ECOS-ONTO-011（不 500 掩蔽）")
    void addVersionDuplicateKeyIs409WithOwnerCode() {
        when(caliberService.findByCode(CODE)).thenReturn(java.util.Optional.of(headRow()));
        when(caliberService.insertVersion(eq("cid-1"), eq("2"), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException(
                        "duplicate key value violates unique constraint \"uniq_ecos_caliber_ver\""));
        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("2");

        ApiResponse<?> resp = controller.addVersion(CODE, dto);

        assertEquals(409, resp.getCode());
        assertEquals(OntologyErrorCode.ECOS_ONTO_011, resp.getErrorCode());
    }

    @Test
    @DisplayName("addVersion 版本幂等腿：撞唯一键后不重复产第二行（insertVersion 恰好一次）")
    void addVersionDuplicateKeyDoesNotDoubleWrite() {
        when(caliberService.findByCode(CODE)).thenReturn(java.util.Optional.of(headRow()));
        when(caliberService.insertVersion(eq("cid-1"), eq("2"), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate key on uniq_ecos_caliber_ver"));
        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("2");

        controller.addVersion(CODE, dto);

        verify(caliberService).insertVersion(eq("cid-1"), eq("2"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("addVersion 正向腿：显式 version_no 首发 ⇒ 200 且不带 011（409 腿不误伤正常升版）")
    void addVersionFirstTimeIsSuccess() {
        when(caliberService.findByCode(CODE)).thenReturn(java.util.Optional.of(headRow()));
        Map<String, Object> versionRow = new LinkedHashMap<>();
        versionRow.put("version_no", "2");
        when(caliberService.insertVersion(eq("cid-1"), eq("2"), any(), any(), any(), any()))
                .thenReturn(versionRow);
        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("2");

        ApiResponse<?> resp = controller.addVersion(CODE, dto);

        assertEquals(0, resp.getCode(), "首发版本必须成功，禁被幂等腿误伤");
        // 成功信封：success 工厂把 "ok" 落进 message 槽（errorCode 键 @JsonInclude(NON_NULL) 不序列化），
        // 断言机读码面不带 011＝409 幂等腿零误伤。
        assertEquals("ok", resp.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(resp.getErrorCode(),
                "成功信封不得携带失败机读码（含 011）");
    }
}
