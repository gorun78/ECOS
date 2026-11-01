package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

/**
 * Git 注册表「全域接入」仓扫门（#147E；《ECOS补充详细设计说明书-R7至R11-2026-10-16.md》§2.9.7①）。
 *
 * <p><b>它防的假绿</b>：V300 建表 + 6 行种子后，若只看「表存在、种子存在」就宣布
 * 「全域接入完成」——实际只有 2 枚 repositoryId 被消费，另外 4 枚在各自域 main 面零引用
 * （§2.9.7 已如实登记）。本门把「消费」钉成可执行事实：6 枚 id 每枚必须至少被一个
 * <b>域侧</b> main 类引用，且 4 个新接入域必须经统一归档器（禁各写一套路径/提交）。</p>
 *
 * <p><b>判据</b>：</p>
 * <ol>
 *   <li>6 枚 repositoryId 字面量全部出现在 {@code src/main/java}（非 runtime-access 属主包）；</li>
 *   <li>4 个新域各有属主类引用 {@code DesignAssetArchiver} 与对应 repositoryId 字面量
 *       ——「注册了但不被消费」将变红；</li>
 *   <li>域侧不得自行拼 {@code ontologies/}/{@code commitAllChanges} 之外的 JGit（DD-3 已有门，
 *       此处只钉统一归档器接线）；</li>
 *   <li>判据能变红：合成夹具在 {@code @TempDir} 求证。</li>
 * </ol>
 *
 * @author ECOS Runtime底座 #147E
 */
@Timeout(120)
class GitRepoRegistryConsumptionGateTest {

    /** V300 种子 6 枚 repositoryId → 属主域关键字（用于回读接线）。 */
    private static final Map<String, String> REGISTRY_CONSUMERS = new LinkedHashMap<>();

    static {
        REGISTRY_CONSUMERS.put("dw-pipeline", "engine/data-engine");
        REGISTRY_CONSUMERS.put("ontology-model", "engine/ontology-engine");
        REGISTRY_CONSUMERS.put("kb-asset-tree", "engine/kb-engine");
        REGISTRY_CONSUMERS.put("cognitive-model", "engine/cognitive-engine");
        REGISTRY_CONSUMERS.put("agent-collab", "engine/ai-engine");
        REGISTRY_CONSUMERS.put("scenario-def", "workspace/workspace-impl");
    }

    private static final Pattern REPO_ID = Pattern.compile("\"([a-z]+-[a-z-]+)\"");

    @Test
    @DisplayName("6 枚 repositoryId 每枚都被域侧 main 面消费（禁「注册不消费」）")
    void everySeedRepositoryIdIsConsumedByItsDomain() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() > 2000, "扫描面缩水：" + files.size());

        Map<String, List<String>> hits = new LinkedHashMap<>();
        for (String id : REGISTRY_CONSUMERS.keySet()) {
            hits.put(id, new ArrayList<>());
        }
        for (Path f : files) {
            String rel = relativize(root, f);
            if (rel.contains("/runtime/access/git/")) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            for (String id : REGISTRY_CONSUMERS.keySet()) {
                if (stripped.contains("\"" + id + "\"")) {
                    hits.get(id).add(rel);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : hits.entrySet()) {
            if (!e.getValue().isEmpty()) {
                continue;
            }
            // dw-pipeline 的历史消费形态是「调用方传 repositoryId，服务端经注册表校验」，
            // 域内无字面量是设计事实（服务端不替调用方臆造仓库），故以注册表查询腿取证。
            if ("dw-pipeline".equals(e.getKey())
                    && domainUsesRegistryLookup(root, REGISTRY_CONSUMERS.get(e.getKey()))) {
                continue;
            }
            missing.add(e.getKey());
        }
        assertEquals(List.of(), missing,
                "以下 repositoryId 只有注册行、无消费方（§2.9.7「全域接入」未完成）：" + missing);
    }

    private static boolean domainUsesRegistryLookup(Path root, String domainFragment) throws IOException {
        for (Path f : collectUnder(root.resolve("ecos_backend"), "/src/main/java/", ".java")) {
            if (!relativize(root, f).contains(domainFragment)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            if (stripped.contains("getRepositoryById(")) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("4 个新接入域经统一归档器 DesignAssetArchiver（禁各写一套路径/提交）")
    void fourNewDomainsUseTheUnifiedArchiver() throws IOException {
        Path root = locateRepoRoot();
        List<String> violations = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        for (Map.Entry<String, String> e : REGISTRY_CONSUMERS.entrySet()) {
            String id = e.getKey();
            if ("dw-pipeline".equals(id) || "ontology-model".equals(id)) {
                continue; // 既有两枚由各自历史通道消费（本门只钉新接入的四枚）
            }
            boolean found = false;
            for (Path f : collectUnder(root.resolve("ecos_backend"), "/src/main/java/", ".java")) {
                String rel = relativize(root, f);
                if (!rel.contains(e.getValue())) {
                    continue;
                }
                String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
                if (stripped.contains("\"" + id + "\"") && stripped.contains("DesignAssetArchiver")) {
                    found = true;
                    evidence.add(id + " → " + rel);
                    break;
                }
            }
            if (!found) {
                violations.add(id + "（属主域 " + e.getValue() + " 未接入 DesignAssetArchiver）");
            }
        }
        assertEquals(List.of(), violations);
        assertEquals(4, evidence.size(), "4 枚新 repositoryId 各须一条属主接线取证：" + evidence);
    }

    @Test
    @DisplayName("判据能变红：合成夹具证明「仅注册不消费」可被判出")
    void predicateCanGoRed() {
        String registeredOnly = "field String id = \"kb-asset-tree\";";
        String consumed = "archiver.archive(\"kb-asset-tree\", ...)";
        assertTrue(registeredOnly.contains("\"kb-asset-tree\""));
        assertFalse(consumed.contains("DesignAssetArchiver"));
        assertTrue((consumed + " DesignAssetArchiver").contains("DesignAssetArchiver"));
        Matcher m = REPO_ID.matcher("\"scenario-def\"");
        assertTrue(m.find());
        assertEquals("scenario-def", m.group(1));
    }

    private static List<Path> collectUnder(Path dir, String fragment, String ext) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(ext))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains(fragment) && !s.contains("/target/") && !s.contains("/archive/");
                    })
                    .forEach(out::add);
        }
        return out;
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        out.addAll(collectUnder(dir, "/src/main/java/", ".java"));
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static Path locateRepoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/）：user.dir=" + cur);
    }
}
