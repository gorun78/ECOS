package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CI 触发面门禁（计划 §六）：改了什么就必须跑对应的 job。
 *
 * <p><b>为什么需要</b>：`ecos-cli-ci.yml` 的 `push` 与 `pull_request` 是两份独立 paths 列表，
 * 极易漂移。实测曾出现「push 侧含 `CliContractParityTest.java`、PR 侧漏掉」——
 * 结果**恰好是「把跨模块门禁改松」的 PR 不会触发该 job**，比不设门禁更糟。</p>
 *
 * <p>本用例只读工作流文本，断言两侧对 CLI 相关触发面保持一致，且三个必需面齐全。</p>
 */
class CiTriggerCoverageTest {

    private static final String WORKFLOW_REL = ".github/workflows/ecos-cli-ci.yml";

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("tools").resolve("ecos-cli"))) {
            dir = dir.getParent();
        }
        org.junit.jupiter.api.Assertions.assertNotNull(dir, "未定位到仓根");
        return dir;
    }

    private static String workflow() throws Exception {
        Path f = repoRoot().resolve(WORKFLOW_REL);
        assertTrue(Files.isRegularFile(f), "工作流缺失: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** 取某个 trigger（push / pull_request）块下的 paths 条目。 */
    private static List<String> pathsOf(String text, String trigger) {
        int start = text.indexOf("  " + trigger + ":");
        assertTrue(start >= 0, "工作流缺 " + trigger + " 触发块");
        int paths = text.indexOf("paths:", start);
        assertTrue(paths > start, trigger + " 块缺 paths");
        List<String> out = new ArrayList<>();
        int i = text.indexOf('\n', paths);
        while (i > 0 && i + 1 < text.length()) {
            int nl = text.indexOf('\n', i + 1);
            String line = text.substring(i + 1, nl > 0 ? nl : text.length()).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i = nl; // 注释/空行不终止（paths 列表允许夹注释）
                continue;
            }
            if (!line.startsWith("-")) {
                break;
            }
            out.add(line.substring(1).trim().replace("'", "").replace("\"", ""));
            i = nl;
        }
        return out;
    }

    @Test
    @DisplayName("push 与 pull_request 的 paths 对 CLI 触发面完全一致")
    void pushAndPullRequestPathsAreSymmetric() throws Exception {
        String wf = workflow();
        List<String> push = pathsOf(wf, "push");
        List<String> pr = pathsOf(wf, "pull_request");
        assertFalse(push.isEmpty(), "push paths 为空");
        assertFalse(pr.isEmpty(), "pull_request paths 为空");
        for (String p : push) {
            assertTrue(pr.contains(p),
                    "push 侧有、PR 侧漏：" + p + "（改门禁本身的 PR 将不触发该 job）");
        }
    }

    @Test
    @DisplayName("CLI 侧三大触发面齐全：工程源、契约源、跨模块契约门禁")
    void requiredSurfacesAreCovered() throws Exception {
        List<String> push = pathsOf(workflow(), "push");
        assertTrue(push.contains("tools/ecos-cli/**"), "缺 CLI 工程源触发面");
        assertTrue(push.contains("docs/10-架构/api-contracts/**"), "缺契约源触发面");
        boolean gate = push.stream().anyMatch(p -> p.endsWith("CliContractParityTest.java"));
        assertTrue(gate, "缺跨模块契约门禁触发面（改门禁不会跑 cli-contract）");
    }

    @Test
    @DisplayName("三个 job 齐全（build / contract / e2e）")
    void allThreeJobsExist() throws Exception {
        String wf = workflow();
        for (String job : List.of("cli-build:", "cli-contract:", "cli-e2e:")) {
            assertTrue(wf.contains(job), "工作流缺 job: " + job);
        }
    }
}
