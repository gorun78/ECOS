package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * F02-08-5 验收《DqGovernanceEndpointReachabilityTest》— DQ 治理工单列表端点可达性
 * （设计分册 02 F02-08，M0 缺口收口，2026-09-30 起算）。
 *
 * <p>缺陷签名（D-6a）：{@code DqWorkOrderServiceImpl.listWorkOrders} 的 {@code @Select} 风
 * 格查询早期版本误查配置表 {@code ecs_sys_config}（FROM 子句缺陷），导致工单列表返回
 * 配置行 / 空集，治理端点
 * {@code /api/v1/dq/work-orders}（及 rules|scores/system|health/selfcheck 同族）不可达或
 * 语义错误。本测试双轨锁死该签名不可复现：</p>
 *
 * <ol>
 *   <li><b>源码扫描</b>：对 {@code DqWorkOrderServiceImpl.java} 中
 *       {@code listWorkOrders} 方法体内出现的 SQL 片段断言 ——
 *       {@code ecs_sys_config} 出现次数 == 0（缺陷 FROM 已消失）；
 *       正确表 {@code dq_work_order} 出现次数 ≥ 1（ST07 schema 限定单源表回归可见）。</li>
 *   <li><b>行为</b>：mock {@code JdbcTemplate}（无 DB），
 *       {@code listWorkOrders(null)} 返回固定集（含一行 {@code dq_severity=CRITICAL}），
 *       断言返回 DTO 含该行；同时捕获实拼 SELECT，断言其 FROM 含
 *       {@code ecos_dq.dq_work_order}（W48/MC06 schema 限定表名）。</li>
 * </ol>
 *
 * <p>禁止项：不落 404 掩盖（F02-08 规格 1，GlobalExceptionHandler 侧另有
 * NoFourOhFourMaskingTest）；本测试不引入 Spring 上下文，纯单测 + Mockito。</p>
 *
 * @author ecos-p0-data-engine
 * <p>2026-09-30（F02-08-5 / W48 关联行：dq_work_order 单源表契约）</p>
 */
@DisplayName("F02-08-5：DQ 工单列表端点可达性（缺陷 FROM ecs_sys_config 签名已消除）")
class DqGovernanceEndpointReachabilityTest {

    private static final Path IMPL_REL = Paths.get(
            "src", "main", "java", "com", "chinacreator", "gzcm", "engine", "data",
            "quality", "service", "DqWorkOrderServiceImpl.java");

    private static final String DEFECT_TABLE = "ecs_sys_config";
    private static final String CORRECT_TABLE = "dq_work_order";

    /** 源码扫描：listWorkOrders 的 SQL 不再误查 ecs_sys_config（缺陷签名 == 0）。 */
    @Test
    @DisplayName("源码扫描：listWorkOrders SQL 不含 ecs_sys_config，但含 dq_work_order")
    void listWorkOrdersSqlDefectSignatureIsGone() throws IOException {
        String src = readSource();
        String methodBody = sliceMethod(src, "listWorkOrders");

        long defectCount = countOccurrences(methodBody, DEFECT_TABLE);
        assertTrue(defectCount == 0,
                "F02-08-5 缺陷签名复活：listWorkOrders 方法体内出现 " + DEFECT_TABLE
                        + " × " + defectCount + "（FROM 子句必须查询工单表，禁配置表）");

        long correctCount = countOccurrences(methodBody, CORRECT_TABLE);
        assertTrue(correctCount >= 1,
                "listWorkOrders 方法体未引用正确表 " + CORRECT_TABLE
                        + "（ST07/MC06 单源表契约）");

        // 全文件兜底：即使方法切片失败，整个实现文件也不得依赖 ecs_sys_config
        //（工单服务与 sysman 配置表无任何耦合 — 跨引擎不操作对方表，铁律 3.3）
        long fileDefectCount = countOccurrences(src, DEFECT_TABLE);
        assertTrue(fileDefectCount == 0,
                "DqWorkOrderServiceImpl 全文件出现 " + DEFECT_TABLE + " × " + fileDefectCount
                        + "：工单服务禁止耦合 sysman 配置表");
    }

    /** 行为（mock JdbcTemplate，无 DB）：CRITICAL 行必须进入返回 DTO，FROM 指向正确表。 */
    @Test
    @DisplayName("行为（mock JdbcTemplate）：CRITICAL 行进入返回 DTO 且 FROM 为 ecos_dq.dq_work_order")
    void listWorkOrdersReturnsCriticalRowViaQualifiedTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class),
                (Object[]) any()))
                .thenReturn(1L);

        DqWorkOrderVO critical = new DqWorkOrderVO();
        critical.setId("wo-1");
        critical.setOrderNo("WO-20260930-001");
        critical.setTitle("[CRITICAL] amount null ratio");
        critical.setStatus("PENDING");
        critical.setSeverity("CRITICAL");
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),
                (RowMapper<DqWorkOrderVO>) any(), anyInt(), anyInt()))
                .thenReturn(List.of(critical));

        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(jdbc);
        @SuppressWarnings("unchecked")
        ObjectProvider<DqKnowledgeSinkService> sinkProvider = mock(ObjectProvider.class);
        when(sinkProvider.getIfAvailable()).thenReturn(null);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), jdbcProvider, sinkProvider);

        // 端点行为：query=null（无过滤）也必须返回可用分页结果，禁 500/404 掩盖
        PageResult<DqWorkOrderVO> page = svc.listWorkOrders(null);
        assertNotNull(page, "listWorkOrders(null) 必须返回非空 PageResult（端点可达，非 404 语义）");

        // 捕获分页 SELECT 实拼 SQL（验证 FROM 正确表 + 缺陷表缺席）
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<DqWorkOrderVO>>any(),
                anyInt(), anyInt());
        String sql = sqlCaptor.getValue();
        String norm = sql.toLowerCase();
        assertTrue(norm.contains("from ecos_dq." + CORRECT_TABLE.toLowerCase()),
                "分页 SELECT 的 FROM 必须为 schema 限定的 " + CORRECT_TABLE
                        + "（W48/MC06）— 实际 SQL：" + sql);
        assertTrue(!norm.contains(DEFECT_TABLE.toLowerCase()),
                "分页 SELECT 不得包含 " + DEFECT_TABLE + " — 实际 SQL：" + sql);

        // CRITICAL 行透传
        assertNotNull(page.getData(), "PageResult.data 不可为 null");
        assertTrue(page.getData().stream()
                        .anyMatch(vo -> "CRITICAL".equals(vo.getSeverity())),
                "返回 DTO 必须包含 severity=CRITICAL 的行（mock 固定集透传）");
    }

    // ==================== 工具 ====================

    private static String readSource() throws IOException {
        Path impl = findModuleRoot().resolve(IMPL_REL);
        assertTrue(Files.isRegularFile(impl), "缺少实现源文件: " + impl);
        return Files.readString(impl, StandardCharsets.UTF_8);
    }

    /** 切出方法从签名行到文件尾的文本（本类方法体顺序已知；宁可多切不可漏切）。 */
    private static String sliceMethod(String src, String methodName) {
        int idx = src.indexOf("public PageResult<DqWorkOrderVO> " + methodName);
        if (idx < 0) {
            idx = src.indexOf(methodName + "(");
        }
        assertTrue(idx >= 0, "未定位到 " + methodName + " 方法（文件结构漂移？）");
        return src.substring(idx);
    }

    private static long countOccurrences(String text, String token) {
        long count = 0;
        int i = 0;
        while ((i = text.indexOf(token, i)) >= 0) {
            count++;
            i += token.length();
        }
        return count;
    }

    static Path findModuleRoot() {
        // 单测 user.dir = 模块根（data-engine-impl）；向上找兜底（含 quality/service 实现的目录）
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve(
                    "src/main/java/com/chinacreator/gzcm/engine/data/quality/service/DqWorkOrderServiceImpl.java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法定位 data-engine-impl 模块根: user.dir="
                + System.getProperty("user.dir"));
    }
}
