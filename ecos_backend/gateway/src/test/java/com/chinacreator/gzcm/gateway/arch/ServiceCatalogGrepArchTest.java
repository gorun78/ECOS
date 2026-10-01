package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W53 ServiceCatalog 收口门禁（详细设计-02 M0 场景核验 / REQ-DATA）。
 *
 * <p>5 大可用性场景（权威 F02-14 M0 表：sysman down / MinIO down / OLAP down /
 * DQ 告警 down / GitRepoRootResolver 缺目录 / DuckDB optional）要求服务目录
 * 在生产路径里<b>不得</b>残留 docker/workdir 端口片段（451/452/453/454/459）
 * ——这些是开发机 debug 端口，混入生产返回体会误导探测方把"本地 dev 端点"
 * 当成"服务不可用原因"。</p>
 *
 * <p>本文件三项收口：</p>
 * <ol>
 *   <li>{@code gatewayResourcesNoDevPortFragments}：扫 {@code gateway/src/main/resources/}
 *       （yml + properties）断言 451|452|453|454|459 与 "Apply for docker/workdir"
 *       零命中（生产合法端口不在此 45x 段）。</li>
 *   <li>{@code winTasksScriptAudit}（informational）：统计 {@code _win_tasks/*.ps1}
 *       中 {@code \bcurl\b} 命中数（开发探测允许 >0，<b>不 fail</b>），并对
 *       {@code Set-Content} 写目标做"破坏性脚本" sanity（当前基线：
 *       _pathdump.ps1 与 docs-migration.ps1 各 1 处，均写预期产物，非破坏性）。</li>
 *   <li>MockMvc {@code GET /api/v1/gateway/service-endpoints} 行为验收：<b>跳过</b>。
 *       本仓无该控制器（grep {@code service-endpoints} 仅命中 runtime 的
 *       {@code ServiceEndpointResolver} 及跨引擎 arch test，无 gateway Controller
 *       暴露此端点）；强行 MockMvc 需新增 controller ≠ 本收口范围（API 只增不改）。
 *       故 W53 行为面以 source-scan 收口即可。</li>
 * </ol>
 *
 * <p><b>devPortFragments baseline（2026-09-30 实查）</b>：grep 451-459 于
 * gateway resources = 0 命中（健康）。</p>
 *
 * @author ECOS Gateway Team / W53
 */
@DisplayName("W53 服务目录收口：生产路径无 dev-port 片段 + 运维脚本审计")
class ServiceCatalogGrepArchTest {

    /** 生产路径禁止出现的开发机端口片段 / docker-workdir 字样。 */
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "451", "452", "453", "454", "459", "Apply for docker", "docker/workdir");

    private static Path gatewayResources() {
        Path p = Paths.get("src/main/resources").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(p), "gateway resources 目录缺失: " + p);
        return p;
    }

    @Test
    @DisplayName("gateway/src/main/resources 无 dev-port 45x 片段与 docker/workdir 字样")
    void gatewayResourcesNoDevPortFragments() throws IOException {
        Path root = gatewayResources();
        Set<String> hits = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
               .filter(p -> p.toString().endsWith(".yml")
                       || p.toString().endsWith(".yaml")
                       || p.toString().endsWith(".properties"))
               .forEach(p -> {
                   try {
                       String src = Files.readString(p).toLowerCase(Locale.ROOT);
                       for (String frag : FORBIDDEN_FRAGMENTS) {
                           if (src.contains(frag.toLowerCase(Locale.ROOT))) {
                               hits.add(root.relativize(p) + " :: " + frag);
                           }
                       }
                   } catch (IOException e) {
                       fail("读文件失败: " + p);
                   }
               });
        }
        assertTrue(hits.isEmpty(),
                "生产路径不应出现 dev-port/docker-workdir 片段（W53 5 场景探测依赖生产端点面）: " + hits);
    }

    @Test
    @DisplayName("信息性审计：_win_tasks/*.ps1 curl 计数（允许>0）+ Set-Content 非破坏性 sanity")
    void winTasksScriptAudit() throws IOException {
        Path winTasks = Paths.get("..", "..", "_win_tasks").toAbsolutePath().normalize();
        // _win_tasks 位于仓库根（gateway/../../_win_tasks）
        assertTrue(Files.isDirectory(winTasks), "_win_tasks 未找到: " + winTasks);

        int[] curlCount = {0};
        Set<String> setContentTargets = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.list(winTasks)) {
            walk.filter(Files::isRegularFile)
               .filter(p -> p.toString().endsWith(".ps1"))
               .forEach(p -> {
                   try {
                       for (String line : Files.readAllLines(p)) {
                           String lower = line.toLowerCase(Locale.ROOT);
                           // 粗粒度 \bcurl\b 词边界
                           if (lower.matches(".*\\bcurl\\b.*")) {
                               curlCount[0]++;
                           }
                           if (lower.contains("set-content")) {
                               setContentTargets.add(p.getFileName() + " :: " + line.trim());
                           }
                       }
                   } catch (IOException e) {
                       fail("读脚本失败: " + p);
                   }
               });
        }
        // informational：curl 计数把数量打到 stdout，不设上限（开发探测合法）
        System.out.println("[ServiceCatalogGrepArchTest] _win_tasks curl 命中行数 = " + curlCount[0]);
        System.out.println("[ServiceCatalogGrepArchTest] Set-Content 目标（人工 review 基线）:");
        setContentTargets.stream().sorted().forEach(t -> System.out.println("    " + t));

        // sanity：Set-Content 的写目标不得指向"删除/重置/格式化"类脚本或外部系统路径。
        // 当前基线 = _pathdump.ps1（写 $out 环境变量指向的本机输出）+ docs-migration.ps1（写 KV 文档），均合法。
        for (String t : setContentTargets) {
            String lower = t.toLowerCase(Locale.ROOT);
            assertTrue(!lower.contains("remove-item") && !lower.contains("format-disk")
                            && !lower.contains("clear-disk") && !lower.contains("rd /s"),
                    "疑似破坏性 Set-Content 目标（需人工确认）: " + t);
        }
        // 至少确认审到脚本了（避免 _win_tasks 误放导致静默跳过）
        assertTrue(setContentTargets.size() >= 1,
                "预期 _win_tasks 至少 1 处 Set-Content（现基线 2 处）；为 0 说明脚本目录变了");
    }
}
