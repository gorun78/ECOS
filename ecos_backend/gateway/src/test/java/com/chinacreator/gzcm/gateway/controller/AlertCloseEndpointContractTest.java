package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/v1/monitor/alerts/{id}/close}（详设-00 §3.3 L2124 {@code closeAlert}）契约门禁
 * —— #142/B4-1 端点缺口第 1 项。
 *
 * <p>钉死四件事：
 * <ol>
 *   <li>响应形态 = 契约的 {@code {id,status:"closed",closeTime}} 三键，
 *       且 {@code closeTime} <b>取写后回查的真实列值</b>，列缺值时如实 {@code null}（不用应用侧时间冒充）；</li>
 *   <li>「更新 0 行」必须区分 <b>404（记录不存在/软删）</b> 与 <b>400（状态不允许）</b>，
 *       不照抄 {@code ack} 的合并口径；</li>
 *   <li>依赖异常 ⇒ 503 fail-closed，不返回成功；</li>
 *   <li>档位 = 契约「L2 及以上」（{@code /api/v1/monitor} 前缀默认只有 L1，必须方法级补齐），
 *       同时回补既有 {@code ack} 的同一档位；DAO 的 SQL 只允许 {@code open/acked} 进入 closed
 *       且带软删过滤。</li>
 * </ol>
 *
 * <p><b>#142/B4-3e-32 追加的第五件事</b>：上述 404/400/503 原先只写在信封 {@code code} 里，
 * 方法返裸 {@code ApiResponse} ⇒ Spring 落的状态行恒 <b>HTTP 200</b>（D-B4-59 裸回族的类型见证腿，
 * 由 B4-3e-31 补盲的棘轮门计量）。现 {@code close} 返 {@code ResponseEntity}，本门禁每条腿
 * 同时断言「状态行 ＝ 契约期望值」与「信封 code ＝ 状态行」，两样缺一即红——
 * 只断言信封会让状态行回退成 200 而测试照绿（＝假绿），只断言状态行则信封改码无人管。</p>
 */
class AlertCloseEndpointContractTest {

    private static final Timestamp CLOSE_TS = Timestamp.valueOf("2026-10-15 09:30:00");

    /** 成功腿：状态行 200 且信封 code=0（写状态行不得把成功腿一起改判）。 */
    private static ApiResponse<Map<String, Object>> okEnvelope(
            ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        assertEquals(200, resp.getStatusCode().value(), "成功腿状态行必须仍是 200");
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(0, body.getCode(), "关闭成功必须 code=0，实际 errorCode=" + body.getErrorCode());
        return body;
    }

    /** 失败腿：状态行与信封 code 同号（裸回时代状态行恒 200，此处即变红点）。 */
    private static ApiResponse<Map<String, Object>> failureEnvelope(
            ResponseEntity<ApiResponse<Map<String, Object>>> resp, int expected) {
        assertEquals(expected, resp.getStatusCode().value(),
                "状态行必须等于契约期望的 " + expected + "，实际=" + resp.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(expected, body.getCode(), "信封 code 必须与状态行同号，实际=" + body.getCode());
        return body;
    }

    /** 可控仓储：记录调用次数与回查内容，不连库。 */
    private static final class FakeRepo extends RuntimeAlertRepository {
        int closeCalls;
        int closeUpdated;
        List<Map<String, Object>> found = List.of();
        RuntimeException throwOnClose;

        FakeRepo() {
            super(null);
        }

        @Override
        public int closeAlert(String id) {
            closeCalls++;
            if (throwOnClose != null) throw throwOnClose;
            return closeUpdated;
        }

        @Override
        public List<Map<String, Object>> findAlertById(String id) {
            return found;
        }
    }

    @Test
    @DisplayName("成功关闭：code=0 + {id,status:closed,closeTime}，closeTime 取回查真实列值")
    void closeReturnsContractShapeWithRealCloseTime() {
        FakeRepo repo = new FakeRepo();
        repo.closeUpdated = 1;
        repo.found = List.of(Map.of("id", "a-1", "status", "closed", "close_time", CLOSE_TS));

        ApiResponse<Map<String, Object>> resp =
                okEnvelope(new RuntimeAlertV1Controller(repo).close("a-1"));

        Map<String, Object> data = resp.getData();
        assertNotNull(data);
        assertEquals("a-1", data.get("id"));
        assertEquals("closed", data.get("status"));
        assertEquals(CLOSE_TS, data.get("closeTime"),
                "closeTime 必须是 ecos_runtime_alert_record.close_time 的真实值（契约 L2124）");
        assertEquals(1, repo.closeCalls);
    }

    @Test
    @DisplayName("响应键集合 = 契约三键：V242 无 close_by ⇒ 不臆造第四键")
    void responseCarriesExactlyContractKeys() {
        FakeRepo repo = new FakeRepo();
        repo.closeUpdated = 1;
        repo.found = List.of(Map.of("close_time", CLOSE_TS));
        ApiResponse<Map<String, Object>> resp =
                okEnvelope(new RuntimeAlertV1Controller(repo).close("a-1"));
        assertEquals(java.util.Set.of("id", "status", "closeTime"), resp.getData().keySet(),
                "V242 实存列只有 close_time 没有 close_by，响应多出 closeBy 即臆造字段");
    }

    @Test
    @DisplayName("close_time 取不到 ⇒ closeTime=null，不用 NOW() 造时间")
    void missingCloseTimeStaysNull() {
        FakeRepo repo = new FakeRepo();
        repo.closeUpdated = 1;
        repo.found = List.of(Map.of("id", "a-1", "status", "closed"));
        ApiResponse<Map<String, Object>> resp =
                okEnvelope(new RuntimeAlertV1Controller(repo).close("a-1"));
        assertTrue(resp.getData().containsKey("closeTime"));
        assertNull(resp.getData().get("closeTime"), "回查不到列值时必须如实 null");
    }

    @Test
    @DisplayName("更新 0 行 + 查无记录 ⇒ 404 上状态行（不与状态冲突混成 400）")
    void unknownIdIs404() {
        FakeRepo repo = new FakeRepo();
        repo.closeUpdated = 0;
        repo.found = List.of();
        ApiResponse<Map<String, Object>> resp =
                failureEnvelope(new RuntimeAlertV1Controller(repo).close("ghost"), 404);
        assertTrue(resp.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("更新 0 行 + 记录存在（已 closed）⇒ 400 且说明状态约束")
    void alreadyClosedIs400WithReason() {
        FakeRepo repo = new FakeRepo();
        repo.closeUpdated = 0;
        repo.found = List.of(Map.of("id", "a-1", "status", "closed", "close_time", CLOSE_TS));
        ApiResponse<Map<String, Object>> resp =
                failureEnvelope(new RuntimeAlertV1Controller(repo).close("a-1"), 400);
        assertTrue(resp.getMessage().contains("open"), "400 文案必须给出可达状态集合，实际=" + resp.getMessage());
    }

    @Test
    @DisplayName("仓储异常 ⇒ 503 fail-closed，状态行同号，禁返回成功")
    void repositoryFailureIs503() {
        FakeRepo repo = new FakeRepo();
        repo.throwOnClose = new RuntimeException("db down");
        ApiResponse<Map<String, Object>> resp =
                failureEnvelope(new RuntimeAlertV1Controller(repo).close("a-1"), 503);
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("档位：close 与 ack 都显式 L2（契约「L2 及以上」，前缀默认只有 L1）")
    void clearanceLevelIsTwoOnAckAndClose() throws Exception {
        Method close = RuntimeAlertV1Controller.class.getMethod("close", String.class);
        Method ack = RuntimeAlertV1Controller.class.getMethod("ack", String.class);
        for (Method m : List.of(close, ack)) {
            MinimumClearance mc = m.getAnnotation(MinimumClearance.class);
            assertNotNull(mc, m.getName() + " 缺 @MinimumClearance ⇒ 会落 /api/v1/monitor 默认 L1，低于契约 L2");
            assertEquals(2, mc.level(), m.getName() + " 档位必须为契约的 L2");
        }
    }

    @Test
    @DisplayName("路径：类前缀 /api/v1/monitor + /alerts/{id}/close 为 POST")
    void mappingIsPostUnderMonitorPrefix() throws Exception {
        RequestMapping rm = RuntimeAlertV1Controller.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm);
        assertTrue(List.of(rm.value()).contains("/api/v1/monitor"));
        Method close = RuntimeAlertV1Controller.class.getMethod("close", String.class);
        PostMapping pm = close.getAnnotation(PostMapping.class);
        assertNotNull(pm, "closeAlert 契约方法是 POST");
        assertEquals("/alerts/{id}/close", pm.value()[0]);
    }

    @Test
    @DisplayName("DAO 写通道：只允许 open/acked 进入 closed 且过滤软删（防越权复活/误关已删记录）")
    void daoSqlGuardsStatusAndSoftDelete() throws IOException {
        String sql = extractCloseAlertSql();
        assertTrue(sql.contains("status='closed'"), "必须写入 closed 状态，实际 SQL=" + sql);
        assertTrue(sql.contains("close_time=NOW()"), "close_time 必须由 DB 时钟写入（V242 实存列，此前零写者）");
        assertTrue(sql.contains("status IN ('open','acked')"), "可达状态集合必须限定 open/acked");
        assertTrue(sql.contains("is_deleted = 0"), "写通道必须带软删过滤（IR03 口径下新增写入面一律带）");
    }

    /** 取 {@code RuntimeAlertDao.closeAlert} 的 SQL 文本（同 commit 单源，禁测试里另抄一份 SQL）。 */
    private static String extractCloseAlertSql() throws IOException {
        Path p = Paths.get("src/main/java/com/chinacreator/gzcm/gateway/dao/RuntimeAlertDao.java");
        if (!Files.exists(p)) {
            p = Paths.get("..", "gateway", "src/main/java/com/chinacreator/gzcm/gateway/dao/RuntimeAlertDao.java");
        }
        // 文件取不到必须 FAIL：静默跳过等于把这条门禁变成恒 PASS（差距文档 D-15 那类假绿）
        assertTrue(Files.exists(p), "RuntimeAlertDao 源文件不可达，无法校验 close SQL：" + p.toAbsolutePath());
        String src = Files.readString(p, StandardCharsets.UTF_8);
        int start = src.indexOf("closeAlert");
        assertTrue(start > 0, "RuntimeAlertDao 里找不到 closeAlert 方法");
        int end = src.indexOf("}", src.indexOf("UPDATE ecos_runtime_alert_record", start));
        assertTrue(end > start, "closeAlert 的 UPDATE 语句解析失败");
        return src.substring(start, end);
    }
}
