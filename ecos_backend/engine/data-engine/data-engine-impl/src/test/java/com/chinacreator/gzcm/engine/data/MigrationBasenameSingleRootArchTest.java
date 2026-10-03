package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W59 / D-18（E.5 lint 纪律单源面，M1）—— 迁移脚本<b>跨根同名</b>漂移护栏（单源 §3.1 碎片棘轮）。
 *
 * <p>背景：D-18 实证单源目录应唯一（{@code gateway/.../db/migration/}），但仓内已存在
 * <b>同名 V*.sql 同时挂在多个根下</b>的碎片——如 gateway 与 services/sysman/impl/sysman-boot
 * 各有一份 {@code V6__ecos_data_quality.sql}，同建 {@code ecos_dq_rule} 但 md5 不同。
 * 铁律 §3.1 要求 DDL 单源禁分域另立目录。本护栏把该单源纪律机械化：</p>
 * <ul>
 *   <li>扫描 6 个仓内 SQL 根（同 {@code _win_tasks/db-migration-lint.ps1} 的 {@code $sqlRoots} 单源口径）：
 *       {@code database / gateway/src/main/resources/db/migration / engine / services / runtime / workspace}。</li>
 *   <li>按 basename 归并所有出现的 root；出现 ≥ 2 个不同 root 的 basename 视为跨根单源碎片。</li>
 *   <li>断言：跨根碎片 basename 集合 <b>⊆</b> 2026-09-30 实查的 14 项 knownLegacy 基线
 *       （V1/V1.1/V2/V3/V4/V5/V6/V7/V8/V9/V10/V11/V12/V13）；任何<b>新增</b>跨根碎片判红（只减不增）。</li>
 *   <li>白线：D-18 点名项 {@code V6__ecos_data_quality.sql} 当前实存且跨根——防本护栏空转的静默绿；
 *       若未来完成 D-18 收敛（V6 单源化到 gateway），此白线将首次判红作撤下线信号，届时应同步下线该 knownLegacy 项。</li>
 * </ul>
 *
 * <p><b>不触库、不 Spring 容器、不联网</b>：纯文件 walk。
 * 与 {@link LegacyDebtBaselineRatchetArchTest#W59_BASELINE_GEN_RANDOM_UUID_FILES} 的分工：
 * 后者锁 {@code gen_random_uuid()}（MC01 红线）违规文件计数只减不增；本护栏锁<b>同名文件跨根重复</b>的
 * 单源碎片面（D-18）——两档从不同维度守住铁律 §3.1 单源纪律，互不重叠。</p>
 */
@DisplayName("W59/D-18 迁移脚本单源碎片 - 跨根同名 V*.sql 基线棘轮（M1）")
class MigrationBasenameSingleRootArchTest {

    /**
     * 2026-09-30 实测基线：跨根同基名 V*.sql 的 14 项 knownLegacy 集合。
     * <b>只减不增</b>：新增跨根同基名 V*.sql 判红；基线项被收敛（不再跨根）则通过并 INFO 提示。
     */
    static final List<String> W59_BASELINE_CROSS_ROOT_DUP_BASENAMES = List.of(
            "V1__init.sql",
            "V1.1__S1_CORE03_workflow_tables.sql",
            "V2__ecos_workflow.sql",
            "V3__ecos_ontology.sql",
            "V4__ecos_agent.sql",
            "V5__ecos_world_model.sql",
            "V6__ecos_data_quality.sql",
            "V7__ecos_glossary_marketplace.sql",
            "V8__ecos_ontology_action.sql",
            "V9__ecos_object_runtime.sql",
            "V10__ecos_world_scenarios.sql",
            "V11__ecos_marketplace_ontology.sql",
            "V12__ecos_sys_dict.sql",
            "V13__ecos_sys_config.sql");

    /** 六个 SQL 根（相对 ecos_backend/），与 db-migration-lint.ps1 的 $sqlRoots 单源口径对齐。 */
    private static final String[] SQL_ROOTS = {
            "database",
            "gateway/src/main/resources/db/migration",
            "engine",
            "services",
            "runtime",
            "workspace"
    };

    @Test
    @DisplayName("跨根同基名 V*.sql 集合 ⊆ knownLegacy 基线（只减不增；白线 V6 实存）")
    void migrationBasename_singleSourceFragmentation_ratchet() throws IOException {
        Path backendRoot = resolveBackendRoot();

        // basename → 出现它的 root 集合（root 用根下相对子路径标识，防 basename 撞但根相同的误判按 same-root 处理）
        TreeMap<String, Set<String>> basenameToRoots = new TreeMap<>();
        for (String root : SQL_ROOTS) {
            Path base = backendRoot.resolve(root);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> isMigration(p.getFileName().toString()))
                        .forEach(p -> basenameToRoots
                                .computeIfAbsent(p.getFileName().toString(), k -> new TreeSet<>())
                                .add(root));
            }
        }

        // 跨根碎片 = 出现在 ≥2 个不同 root 的 basename
        List<String> crossRootDups = new ArrayList<>();
        for (var e : basenameToRoots.entrySet()) {
            if (e.getValue().size() >= 2) {
                crossRootDups.add(e.getKey());
            }
        }
        crossRootDups.sort(String::compareTo);
        assertTrue(!crossRootDups.isEmpty(),
                "白线失守：未发现任何跨根同基名 V*.sql 碎片 —— 要么全部已收敛到单源（D-18 完成），"
                        + "要么本护栏扫描面漂移（六根未命中），请人工复核后决定下线基线");

        // a) 新增碎片判红：所有跨根碎片都必须在基线内
        List<String> notInBaseline = new ArrayList<>();
        for (String b : crossRootDups) {
            if (!W59_BASELINE_CROSS_ROOT_DUP_BASENAMES.contains(b)) {
                notInBaseline.add(b);
            }
        }
        assertTrue(notInBaseline.isEmpty(),
                "发现新增跨根同基名 V*.sql（铁律 §3.1 单源违规，W59/D-18 棘轮判红）：\n  "
                        + String.join("\n  ", notInBaseline)
                        + "\n——DDL 单源目录唯一为 gateway/.../db/migration/，新 V 脚本禁分域另立目录");

        // b) 收敛方向 INFO：基线项若已不再跨根（收敛成功），提示可下调基线（不阻断）
        Set<String> current = new HashSet<>(crossRootDups);
        List<String> gone = new ArrayList<>();
        for (String b : W59_BASELINE_CROSS_ROOT_DUP_BASENAMES) {
            if (!current.contains(b)) {
                gone.add(b);
            }
        }
        if (!gone.isEmpty()) {
            System.out.println("[INFO] W59/D-18 跨根碎片已收敛: " + gone
                    + "（只减不增通过；如需下调基线请同步更新 W59_BASELINE_CROSS_ROOT_DUP_BASENAMES）");
        }

        // c) 白线：D-18 点名项 V6__ecos_data_quality.sql 当前实存且跨 ≥2 根（防整条 D-18 面被单源化后静默变绿）
        Set<String> v6Roots = basenameToRoots.getOrDefault("V6__ecos_data_quality.sql", Set.of());
        assertTrue(v6Roots.size() >= 2,
                "白线撤下信号：V6__ecos_data_quality.sql 跨根数=" + v6Roots.size()
                        + "，D-18 该碎片若已收敛到单源请同步从基线移除此项并撤下本白线");
    }

    /** 迁移主线脚本：{@code V\d(.\d+)?__xxx.sql}（排除 rollback/seed 类归档）。 */
    private boolean isMigration(String name) {
        String low = name.toLowerCase();
        if (low.contains("rollback")) {
            return false;
        }
        return name.matches("^V\\d+(?:\\.\\d+)*__.*\\.sql$");
    }

    /** 自模块 src/main/java 上溯到含 docs + ecos_backend 的仓根，再取 ecos_backend。 */
    private Path resolveBackendRoot() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur.resolve("ecos_backend");
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根（含 docs + ecos_backend）以定位 6 个 SQL 根");
        return null; // unreachable
    }
}
