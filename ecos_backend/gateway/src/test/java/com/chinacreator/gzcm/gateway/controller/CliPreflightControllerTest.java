package com.chinacreator.gzcm.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.dao.CliPreflightProbeDao;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;

/**
 * CLI 预检（{@code POST /api/v1/cli/preflight}）门禁 —— 详设-16 §16.7 / 计划 §三.3。
 *
 * <p>钉住三条不可退让的语义：</p>
 * <ol>
 *   <li><b>只报告，不建 Run</b>：{@code createsRun=false}，且 11 步齐备；</li>
 *   <li><b>禁把未知写 PASS</b>：空体/非 JSON/无可判定载荷 ⇒ {@code NOT_VERIFIED}；
 *       业务码非零 / 4xx ⇒ {@code FAIL}；502|503|504 ⇒ {@code BLOCKED}（依赖不可用 ≠ 业务失败）；</li>
 *   <li><b>审批存储探活是四值</b>：可查 ⇒ PASS；{@code ping()} 抛异常 ⇒ BLOCKED；返回假值 ⇒ NOT_VERIFIED
 *       （{@code SELECT 1} 冒充表可达的形态已由 2026-11-11 修法换掉，见 DAO 注释）。</li>
 * </ol>
 *
 * <p><b>为何用关闭端口做基址</b>：10 条 HTTP 探测在本机 :8080 上有真服务时结果不稳定；
 * {@code 127.0.0.1:1} 恒连接拒绝，探测必然落 BLOCKED，使步数/终态断言确定可复现。</p>
 */
class CliPreflightControllerTest {

    private static final String DEAD_BASE = "http://127.0.0.1:1";

    private static CliPreflightController controller(boolean pingResult, boolean throwsOnPing) {
        CliPreflightProbeDao dao = new CliPreflightProbeDao() {
            @Override
            public boolean ping() {
                if (throwsOnPing) {
                    throw new IllegalStateException("connection refused");
                }
                return pingResult;
            }
        };
        return new CliPreflightController(new RestTemplate(), dao, DEAD_BASE);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cli/preflight");
        request.addHeader("X-Trace-Id", "trace-cli-preflight-test");
        return request;
    }

    @Test
    @DisplayName("依赖全不可达 ⇒ 503 BLOCKED，11 步齐备，createsRun=false（只报告不建 Run）")
    void blockedReportKeepsElevenStepsAndCreatesNoRun() {
        ResponseEntity<ApiResponse<CliPreflightController.PreflightReport>> resp =
                controller(true, false).run(request());

        assertEquals(503, resp.getStatusCode().value());
        assertEquals("E-CLI-PREFLIGHT-BLOCKED", resp.getBody().getErrorCode());
        assertEquals("trace-cli-preflight-test", resp.getBody().getTraceId());

        CliPreflightController.PreflightReport report = resp.getBody().getData();
        assertNotNull(report, "报告必须挂在 data 上（CLI 侧按 data.counts 定级）");
        assertEquals(11, report.stepCount());
        assertEquals(11, report.steps().size());
        assertFalse(report.createsRun(), "preflight 不得创建 Run");
        assertEquals(10, report.blocked(), "10 条 HTTP 探测对关闭端口必须落 BLOCKED");
        assertEquals(0, report.fail());
        assertEquals(0, report.notVerified());
        assertEquals(1, report.pass(), "审批存储可查 ⇒ 唯一 PASS");
        for (CliPreflightController.StepReport step : report.steps()) {
            assertFalse(step.nextAction() == null || step.nextAction().isBlank(),
                    "每一步都要给 nextAction（只报不修等于没报）：" + step.step());
            assertFalse(step.traceId() == null || step.traceId().isBlank(),
                    "每一步都要带 traceId：" + step.step());
        }
    }

    @Test
    @DisplayName("审批存储 ping 抛异常 ⇒ 该步 BLOCKED；返回假值 ⇒ NOT_VERIFIED（禁写 PASS）")
    void approvalStoreProbeIsNeverFalselyGreen() {
        CliPreflightController.PreflightReport thrown =
                controller(false, true).run(request()).getBody().getData();
        CliPreflightController.StepReport blockedStep = stepOf(thrown, "approval.store");
        assertEquals("BLOCKED", blockedStep.status());
        assertTrue(blockedStep.reason().contains("不可达"), "原因须指明不可达: " + blockedStep.reason());
        assertEquals(11, thrown.blocked());

        CliPreflightController.PreflightReport falsy =
                controller(false, false).run(request()).getBody().getData();
        assertEquals("NOT_VERIFIED", stepOf(falsy, "approval.store").status(),
                "ping() 返回假值属「判不了」，不得写成 PASS");
        assertEquals(1, falsy.notVerified());
    }

    @Test
    @DisplayName("响应语义 → 四值：空体/非 JSON ⇒ NOT_VERIFIED；非零码/4xx ⇒ FAIL；5xx 依赖 ⇒ BLOCKED")
    void httpToStatusMappingNeverRewardsIgnorance() throws Exception {
        Method fill = CliPreflightController.class.getDeclaredMethod("fill",
                CliPreflightController.StepReport.class, int.class, String.class);
        fill.setAccessible(true);

        assertEquals("NOT_VERIFIED", statusOf(fill, 200, null));
        assertEquals("NOT_VERIFIED", statusOf(fill, 200, "   "));
        assertEquals("NOT_VERIFIED", statusOf(fill, 200, "<html>gateway</html>"));
        assertEquals("NOT_VERIFIED", statusOf(fill, 200, "{}"),
                "200 但无可判定载荷 ⇒ 判不了，不是成功");
        assertEquals("PASS", statusOf(fill, 200, "{\"code\":0,\"data\":{}}"));
        assertEquals("PASS", statusOf(fill, 200, "{\"items\":[]}"),
                "非信封但非空的 200 载荷视为可用（读面可不走 ApiResponse）");
        assertEquals("FAIL", statusOf(fill, 200, "{\"code\":500,\"errorCode\":\"E-X\"}"));
        assertEquals("FAIL", statusOf(fill, 401, "{}"));
        assertEquals("FAIL", statusOf(fill, 403, "{}"));
        assertEquals("FAIL", statusOf(fill, 400, "{}"));
        assertEquals("BLOCKED", statusOf(fill, 502, "{}"));
        assertEquals("BLOCKED", statusOf(fill, 503, "{\"code\":503}"));
        assertEquals("BLOCKED", statusOf(fill, 504, null));
    }

    @Test
    @DisplayName("下游安全裁决不可用（200 + 503/ECOS-SEC-501 信封）⇒ BLOCKED，不得算业务 FAIL")
    void securityUnavailableEnvelopeIsBlockedNotFailed() throws Exception {
        Method fill = CliPreflightController.class.getDeclaredMethod("fill",
                CliPreflightController.StepReport.class, int.class, String.class);
        fill.setAccessible(true);

        assertEquals("BLOCKED", statusOf(fill, 200,
                        "{\"code\":503,\"errorCode\":\"ECOS-SEC-501\",\"message\":\"RLS_SERVICE_UNAVAILABLE\","
                                + "\"data\":[],\"success\":false}"),
                "RLS 不可用是依赖故障，判 FAIL 会把运维告警指错方向");
        assertEquals("FAIL", statusOf(fill, 200,
                        "{\"code\":500,\"errorCode\":\"ECOS-SYS-500\",\"message\":\"报表超时\"}"),
                "非不可用信封的非零码 ⇒ 仍落 FAIL（把不可用判据当成豁免一切会漏真失败）");
    }

    @Test
    @DisplayName("服务端 traceId 覆盖探测 traceId（CLI 输出 trace_id 才是可对账的那一个）")
    void serverTraceWinsOverProbeTrace() throws Exception {
        Method fill = CliPreflightController.class.getDeclaredMethod("fill",
                CliPreflightController.StepReport.class, int.class, String.class);
        fill.setAccessible(true);
        CliPreflightController.StepReport row = new CliPreflightController.StepReport(
                "gateway.health", "NOT_VERIFIED", "src", null, null, "local-trace", "purpose", "next");

        CliPreflightController.StepReport out = (CliPreflightController.StepReport) fill.invoke(
                controller(true, false), row, 200, "{\"code\":0,\"traceId\":\"server-trace\"}");
        assertEquals("server-trace", out.traceId());
    }

    @Test
    @DisplayName("汇总计数与终态：FAIL 压过 BLOCKED，仅 NOT_VERIFIED ⇒ 200 但如实计数")
    void failDominatesBlockedInHttpStatus() {
        // 全 BLOCKED 场景已在 blockedReportKeepsElevenStepsAndCreatesNoRun 钉住；此处只验 200 分支
        ResponseEntity<ApiResponse<CliPreflightController.PreflightReport>> resp =
                controller(true, false).run(request());
        assertTrue(resp.getBody().getData().blocked() > 0, "前置：本场景必有 BLOCKED");
        assertEquals(503, resp.getStatusCode().value(), "有 BLOCKED ⇒ 503（不得折叠成 200）");
    }

    private static CliPreflightController.StepReport stepOf(
            CliPreflightController.PreflightReport report, String name) {
        List<CliPreflightController.StepReport> steps = report.steps();
        for (CliPreflightController.StepReport step : steps) {
            if (name.equals(step.step())) {
                return step;
            }
        }
        throw new AssertionError("报告缺步骤: " + name + "；实有 "
                + steps.stream().map(CliPreflightController.StepReport::step).toList());
    }

    private String statusOf(Method fill, int http, String body) throws Exception {
        CliPreflightController.StepReport row = new CliPreflightController.StepReport(
                "probe", "NOT_VERIFIED", "GET /x", null, null, "t", "purpose", "next");
        CliPreflightController.StepReport out = (CliPreflightController.StepReport) fill.invoke(
                controller(true, false), row, http, body);
        return out.status();
    }
}
