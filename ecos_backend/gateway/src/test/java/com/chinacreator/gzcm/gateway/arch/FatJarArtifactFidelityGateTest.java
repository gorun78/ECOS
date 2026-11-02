package com.chinacreator.gzcm.gateway.arch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #147W —— fat-jar 制品保真门禁（防「BUILD SUCCESS ≠ 跑的是这份代码」的 stale jar 假绿）。
 *
 * <p><b>本门禁钉住的实证机制</b>（不是猜测）：gateway 是 monolith fat jar，六引擎 + runtime
 * 全部以 {@code BOOT-INF/lib/<artifact>-<version>.jar} 的<b>嵌套 jar</b>形态进来；打包时依赖坐标
 * 若从 {@code ~/.m2} 取，而上一批只重编了模块 {@code target/classes} 没重跑 {@code install}
 * （或跑了 {@code install} 但没 {@code clean}），则 reactor 照样打印 BUILD SUCCESS、fat jar 照样
 * 产出、进程照样起来 —— <b>运行的却是旧字节码</b>。根 {@code AGENTS.md}「其他约束」#1 已把这条例
 * 写在墙上（「Gateway 从 .m2 加载旧 JAR」），但只有口头约定、没有任何断言承接 ⇒ 每个「已实跑验证」
 * 的结论都隔着这一层未证。本门禁把这层变成可判红的事。</p>
 *
 * <p><b>判据（三面一致 + 时效 + 启动形态）</b>：
 * <ol>
 *   <li>{@link #canaryClassBytesThreeWayIdentical()} 对 {@link #CANARIES} 每枚 canary 取
 *       ①fat jar 内嵌 jar 里的 {@code .class} ②模块 {@code target/classes} 里的同名 {@code .class}
 *       ③{@code ~/.m2} 里该 artifact jar 内的同名 {@code .class}，三者 SHA-256 <b>必须全等</b>；
 *       另加「内嵌整 jar 字节 == 模块 {@code target/<artifact>-<version>.jar}」。
 *       任一副本缺失或不等 = 红（缺失不降级为跳过 —— 「没有可对照的副本」本身就是「无法证伪」）。</li>
 *   <li>{@link #nestedLibCoordinatesAreUnique()} 内嵌 lib 同 artifactId 出现两个版本 = 红
 *       （旧版可能胜出，且这种冲突在日志里完全静默）。</li>
 *   <li>{@link #compiledOutputNotOlderThanSource()} canary 的源码 mtime 晚于其 {@code .class}
 *       = 红（改过没重编 ⇒ 一切「已验证」都建立在旧产物上）。</li>
 *   <li>{@link #launchShapeIsPackaged()} MANIFEST 的 {@code Main-Class}/{@code Start-Class}
 *       与 {@code BOOT-INF/classpath.idx} 在位，且 Start-Class 真有编译产物。</li>
 * </ol>
 *
 * <p><b>标志位口径（防「默认全量跑绿 = 本门禁过了」的误读）</b>：上面四条实面腿需要 fat jar 存在，
 * 只在收口批带 {@code -Decos.verify.fatjar=true} 时执行；<b>不带标志的默认跑里这四条不执行</b>，
 * 不得据此宣布 fat jar 保真。标志打开而 jar 不存在 ⇒ 判红（不是跳过）。
 * 反之，谓词本身的活性由不带标志的夹具腿（{@link #predicatesGoRedOnSyntheticFixtures()} 等）
 * 常驻保证 —— 夹具走的是与实面<b>完全相同</b>的谓词代码与目录布局（只是根指向 {@code @TempDir}），
 * 所以「把 canary 清空」「谓词写死返回空」都会在默认全量跑里变红。</p>
 *
 * @author #147W 收口门禁补强
 */
public class FatJarArtifactFidelityGateTest {

    /** 实面腿开关：{@code -Decos.verify.fatjar=true}。 */
    static final String FLAG = "ecos.verify.fatjar";

    /** fat jar 路径覆盖（缺省取 {@code gateway/target/gateway-1.0.0-SNAPSHOT.jar}）。 */
    static final String PATH_FLAG = "ecos.verify.fatjar.path";

    private static final String LIB_PREFIX = "BOOT-INF/lib/";
    private static final String CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String CLASSPATH_IDX = "BOOT-INF/classpath.idx";
    private static final String EXPECTED_START_CLASS =
            "com.chinacreator.gzcm.gateway.GatewayApplication";

    /**
     * canary = 近几批 Git 版本治理链路上「改了但最容易没进 fat jar」的类。
     * 清空本清单会让 {@link #canaryListKeepsCoverage()} 变红（禁把判据剪空当通过）。
     */
    record Canary(String artifactId, String moduleDir, String entryClass) {
    }

    static final List<Canary> CANARIES = List.of(
            new Canary("kb-engine-impl", "engine/kb-engine/kb-engine-impl",
                    "com/chinacreator/gzcm/engine/kb/service/ComplianceRuleVersionService.class"),
            new Canary("ontology-engine-impl", "engine/ontology-engine/ontology-engine-impl",
                    "com/chinacreator/gzcm/engine/ontology/service/OntologyVersionService.class"),
            new Canary("runtime-access", "runtime/runtime-access",
                    "com/chinacreator/gzcm/runtime/access/git/GitServiceImpl.class"));

    /** gateway 自身类不经嵌套 lib，走 {@code BOOT-INF/classes/}（单独一腿，见 {@link #gatewayOwnClassStoredFresh()}）。 */
    private static final String GATEWAY_ENTRY_CLASS =
            "com/chinacreator/gzcm/gateway/filter/CachedBodyHttpServletRequest.class";

    /** 夹具用 canary（与实面同一谓词、同一布局，只把根指向临时目录）。 */
    private static final Canary FIXTURE_CANARY = new Canary(
            "kb-engine-impl", "engine/kb-engine/kb-engine-impl", "com/example/Foo.class");

    // ───────────────────────────── 实面腿（需 -Decos.verify.fatjar=true）─────────────────────────────

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void fatJarMustExistWhenFlagIsOn() {
        Path jar = locateFatJar();
        assertTrue(Files.isRegularFile(jar),
                "带 -D" + FLAG + "=true 却没找到 fat jar：" + jar
                        + " ⇒ 先 package（改过依赖模块则必须 clean install 变更模块再重打包），禁把缺产物当跳过");
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void canaryClassBytesThreeWayIdentical() throws IOException {
        List<String> violations = checkCanaries(locateFatJar(), backendRoot(), m2Root(), CANARIES);
        assertTrue(violations.isEmpty(), "fat jar 制品保真失败：\n" + String.join("\n", violations));
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void canaryHashesPrintedForEvidence() throws IOException {
        Path fatJar = locateFatJar();
        int libCount = 0;
        try (ZipFile zip = new ZipFile(fatJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(LIB_PREFIX) && name.endsWith(".jar")) {
                    libCount++;
                }
            }
        }
        System.out.println("[147W] fatJar=" + fatJar.getFileName() + " nestedLibJars=" + libCount);
        for (Canary c : CANARIES) {
            String nestedName = findLibEntry(fatJar, c.artifactId() + "-");
            assertNotNull(nestedName, "[" + c.artifactId() + "] 未打进 fat jar");
            byte[] nested = readBytesEntry(fatJar, LIB_PREFIX + nestedName);
            byte[] clsFat = jarEntryBytes(nested, c.entryClass());
            byte[] clsModule = Files.readAllBytes(
                    backendRoot().resolve(c.moduleDir()).resolve("target/classes").resolve(c.entryClass()));
            String version = nestedName.substring(c.artifactId().length() + 1,
                    nestedName.length() - ".jar".length());
            Path m2Jar = m2Root().resolve("com/chinacreator/gzcm").resolve(c.artifactId())
                    .resolve(version).resolve(c.artifactId() + "-" + version + ".jar");
            byte[] clsM2 = jarEntryBytes(Files.readAllBytes(m2Jar), c.entryClass());
            System.out.println("[147W] " + c.artifactId() + " class=" + c.entryClass()
                    + " nestedJarBytes=" + nested.length
                    + " sha(fat)=" + head(sha256(clsFat))
                    + " sha(module)=" + head(sha256(clsModule))
                    + " sha(m2)=" + head(sha256(clsM2)));
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void nestedLibCoordinatesAreUnique() throws IOException {
        List<String> dups = duplicateLibCoordinates(locateFatJar());
        assertTrue(dups.isEmpty(), "fat jar 内嵌 lib 存在同 artifactId 多版本（旧代码可能胜出）：\n"
                + String.join("\n", dups));
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void compiledOutputNotOlderThanSource() throws IOException {
        List<String> stale = staleCompiledOutputs(backendRoot(), CANARIES);
        assertTrue(stale.isEmpty(), "canary 源码比编译产物新（改过未重编，验证结论无效）：\n"
                + String.join("\n", stale));
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void launchShapeIsPackaged() throws IOException {
        Path fatJar = locateFatJar();
        String manifest = readTextEntry(fatJar, "META-INF/MANIFEST.MF");
        assertNotNull(manifest, "fat jar 缺 META-INF/MANIFEST.MF：" + fatJar);
        Attributes attrs = new Manifest(new ByteArrayInputStream(manifest.getBytes())).getMainAttributes();
        String mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
        String startClass = attrs.getValue("Start-Class");
        assertNotNull(mainClass, "MANIFEST 缺 Main-Class（Spring Boot Loader 入口）");
        assertEquals(EXPECTED_START_CLASS, startClass,
                "MANIFEST Start-Class 与预期启动类不符（fat jar 不是本项目的 gateway 产物）");
        assertNotNull(readBytesEntry(fatJar, CLASSPATH_IDX),
                "缺 " + CLASSPATH_IDX + " ⇒ 该 jar 不是 boot 重打包产物，运行期类路径顺序不可证");
        // 启动类自己也得有编译产物（否则「Start-Class 写了个不存在的类」照样打得过包）
        Path startClassFile = backendRoot().resolve("gateway/target/classes/"
                + EXPECTED_START_CLASS.replace('.', '/') + ".class");
        assertTrue(Files.isRegularFile(startClassFile),
                "Start-Class 无编译产物：" + startClassFile);
    }

    @Test
    @EnabledIfSystemProperty(named = "ecos.verify.fatjar", matches = "true")
    void gatewayOwnClassStoredFresh() throws IOException {
        List<String> violations = new ArrayList<>();
        byte[] stored = readBytesEntry(locateFatJar(), CLASSES_PREFIX + GATEWAY_ENTRY_CLASS);
        if (stored == null) {
            violations.add("BOOT-INF/classes 缺 " + GATEWAY_ENTRY_CLASS);
        }
        Path local = backendRoot().resolve("gateway/target/classes/" + GATEWAY_ENTRY_CLASS);
        if (!Files.isRegularFile(local)) {
            violations.add("gateway/target/classes 缺 " + GATEWAY_ENTRY_CLASS);
        }
        if (stored != null && Files.isRegularFile(local)) {
            String a = sha256(stored);
            String b = sha256(Files.readAllBytes(local));
            if (!a.equals(b)) {
                violations.add("gateway 自身类字节码不一致：fat jar " + a.substring(0, 12)
                        + " != target/classes " + b.substring(0, 12) + "（" + GATEWAY_ENTRY_CLASS + "）");
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    // ───────────────────────────── 不带标志也常驻的活性/反证腿 ─────────────────────────────

    /** 谓词活性：同一套谓词喂「故意做坏」的夹具必须变红，喂「故意做对」的夹具必须变绿。 */
    @Test
    void predicatesGoRedOnSyntheticFixtures(@TempDir Path tmp) throws IOException {
        // ① 一致夹具 ⇒ 零违规（证明谓词不是恒红）
        Path root = fixtureRoot(tmp.resolve("fresh"), true, false);
        List<String> clean = checkCanaries(root.resolve("fat.jar"), root, root.resolve(".m2/repository"),
                List.of(FIXTURE_CANARY));
        assertTrue(clean.isEmpty(), "一致夹具被判红 ⇒ 谓词不可用：" + String.join(" / ", clean));

        // ② 内嵌 jar 与模块产物字节不同 ⇒ 必须抓到（本门禁的核心场景）
        Path drift = fixtureRoot(tmp.resolve("drift"), false, false);
        List<String> v = checkCanaries(drift.resolve("fat.jar"), drift, drift.resolve(".m2/repository"),
                List.of(FIXTURE_CANARY));
        assertFalse(v.isEmpty(), "内嵌代码与模块编译产物不一致却判绿 ⇒ 门禁失效");

        // ③ fat jar 里压根没有该 canary ⇒ 必须抓到（禁「找不到就跳过」）
        Path missing = fixtureRoot(tmp.resolve("missing"), true, true);
        assertFalse(checkCanaries(missing.resolve("fat.jar"), missing, missing.resolve(".m2/repository"),
                List.of(FIXTURE_CANARY)).isEmpty(), "canary 未打进 fat jar 却判绿 ⇒ 门禁失效");

        // ④ 同 artifactId 双版本内嵌 ⇒ 必须抓到，且单版本夹具不误报
        List<String> dups = duplicateLibCoordinates(
                twoVersionLibFatJar(tmp.resolve("dup.jar")));
        assertEquals(1, dups.size(), "嵌套 lib 双版本未被唯一性谓词抓到：" + dups);
        assertTrue(duplicateLibCoordinates(fixtureRoot(tmp.resolve("uniq"), true, false)
                .resolve("fat.jar")).isEmpty(), "唯一坐标夹具被误报");

        // ⑤ 源码比 class 新 ⇒ 时效谓词必须抓到，等值夹具不误报
        assertFalse(staleCompiledOutputs(freshSourceTree(tmp.resolve("stale"), true)).isEmpty(),
                "源码新于编译产物未被时效谓词抓到");
        assertTrue(staleCompiledOutputs(freshSourceTree(tmp.resolve("fresh-src"), false)).isEmpty(),
                "时效谓词误报");
    }

    /** 判据不得被剪空：canary 清单缩水 = 假绿通道，直接判红。 */
    @Test
    void canaryListKeepsCoverage() {
        assertTrue(CANARIES.size() >= 3,
                "canary 清单少于 3 枚（当前 " + CANARIES.size() + "）：Git 版本治理链路的关键类必须留在清单里");
        long distinct = CANARIES.stream().map(Canary::artifactId).distinct().count();
        assertEquals(CANARIES.size(), distinct, "canary artifactId 重复 ⇒ 覆盖面虚增");
    }

    // ───────────────────────────── 谓词（实面与夹具共用）─────────────────────────────

    /** 三面一致 + 整 jar 一致；返回违规描述，空 = 通过。副本缺失也算违规。 */
    static List<String> checkCanaries(Path fatJar, Path backendRoot, Path m2Root,
                                      List<Canary> canaries) throws IOException {
        List<String> out = new ArrayList<>();
        for (Canary c : canaries) {
            String nestedName = findLibEntry(fatJar, c.artifactId() + "-");
            if (nestedName == null) {
                out.add("[" + c.artifactId() + "] fat jar 内嵌 lib 里没有该 artifact（" + LIB_PREFIX
                        + c.artifactId() + "-*.jar）⇒ 代码根本没打进产物");
                continue;
            }
            byte[] nestedJar = readBytesEntry(fatJar, LIB_PREFIX + nestedName);
            String version = nestedName.substring(c.artifactId().length() + 1,
                    nestedName.length() - ".jar".length());
            byte[] clsInFat = jarEntryBytes(nestedJar, c.entryClass());
            Path moduleClasses = backendRoot.resolve(c.moduleDir()).resolve("target/classes")
                    .resolve(c.entryClass());
            Path moduleJar = backendRoot.resolve(c.moduleDir()).resolve("target")
                    .resolve(c.artifactId() + "-" + version + ".jar");
            Path m2Jar = m2Root.resolve("com/chinacreator/gzcm").resolve(c.artifactId())
                    .resolve(version).resolve(c.artifactId() + "-" + version + ".jar");

            List<String> absent = new ArrayList<>();
            if (clsInFat == null) {
                absent.add("内嵌 jar 缺 class " + c.entryClass());
            }
            byte[] clsInModule = Files.isRegularFile(moduleClasses)
                    ? Files.readAllBytes(moduleClasses) : null;
            if (clsInModule == null) {
                absent.add("模块 target/classes 缺 " + moduleClasses);
            }
            byte[] m2Bytes = Files.isRegularFile(m2Jar) ? Files.readAllBytes(m2Jar) : null;
            if (m2Bytes == null) {
                absent.add(".m2 缺 " + m2Jar + "（未 install，无从核对）");
            }
            byte[] clsInM2 = m2Bytes == null ? null : jarEntryBytes(m2Bytes, c.entryClass());
            if (m2Bytes != null && clsInM2 == null) {
                absent.add(".m2 jar 缺 class " + c.entryClass());
            }
            byte[] moduleJarBytes = Files.isRegularFile(moduleJar)
                    ? Files.readAllBytes(moduleJar) : null;
            if (moduleJarBytes == null) {
                absent.add("模块 target 缺 " + moduleJar);
            }
            if (!absent.isEmpty()) {
                out.add("[" + c.artifactId() + "] 副本缺席，无法证伪：" + String.join(" / ", absent));
                continue;
            }
            String hFat = sha256(clsInFat);
            String hModule = sha256(clsInModule);
            String hM2 = sha256(clsInM2);
            if (!hFat.equals(hModule)) {
                out.add("[" + c.artifactId() + "] class 字节码不一致：fat jar " + head(hFat)
                        + " != 模块 target/classes " + head(hModule) + " ⇒ fat jar 内嵌的是旧编译产物，"
                        + "须 clean install 该模块后重新 package gateway");
            }
            if (!hFat.equals(hM2)) {
                out.add("[" + c.artifactId() + "] class 字节码不一致：fat jar " + head(hFat)
                        + " != .m2 " + head(hM2) + " ⇒ 打包用的 .m2 副本与工作树编译结果分叉");
            }
            if (!sha256(nestedJar).equals(sha256(moduleJarBytes))) {
                out.add("[" + c.artifactId() + "] 整 jar 字节不一致：BOOT-INF/lib 内嵌 "
                        + head(sha256(nestedJar)) + " != " + moduleJar.getFileName() + " "
                        + head(sha256(moduleJarBytes)));
            }
        }
        return out;
    }

    /** 内嵌 lib 同 artifactId 多版本；返回冲突描述列表。 */
    static List<String> duplicateLibCoordinates(Path fatJar) throws IOException {
        Map<String, List<String>> byArtifact = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(fatJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(LIB_PREFIX) || !name.endsWith(".jar")) {
                    continue;
                }
                String file = name.substring(LIB_PREFIX.length());
                String base = stripVersion(file);
                byArtifact.computeIfAbsent(base, k -> new ArrayList<>()).add(file);
            }
        }
        List<String> out = new ArrayList<>();
        byArtifact.forEach((base, files) -> {
            if (files.size() > 1) {
                out.add(base + " → " + files);
            }
        });
        return out;
    }

    /** 源码严格新于 class（= 改过没重编）；返回过时产物列表。 */
    static List<String> staleCompiledOutputs(Path backendRoot, List<Canary> canaries) throws IOException {
        List<String> out = new ArrayList<>();
        for (Canary c : canaries) {
            String clsRel = c.entryClass();
            String javaRel = clsRel.substring(0, clsRel.length() - ".class".length()) + ".java";
            Path classFile = backendRoot.resolve(c.moduleDir()).resolve("target/classes").resolve(clsRel);
            Path sourceFile = backendRoot.resolve(c.moduleDir()).resolve("src/main/java").resolve(javaRel);
            if (!Files.isRegularFile(classFile) || !Files.isRegularFile(sourceFile)) {
                out.add("[" + c.artifactId() + "] 无法比对时效：class=" + (Files.isRegularFile(classFile)
                        ? "有" : "无") + " source=" + (Files.isRegularFile(sourceFile) ? "有" : "无"));
                continue;
            }
            long classMtime = Files.getLastModifiedTime(classFile).toMillis();
            long sourceMtime = Files.getLastModifiedTime(sourceFile).toMillis();
            if (sourceMtime > classMtime) {
                out.add("[" + c.artifactId() + "] 源码比 class 新 " + (sourceMtime - classMtime)
                        + "ms（" + sourceFile.getFileName() + "）⇒ 未重编，此前的验证不代表当前代码");
            }
        }
        return out;
    }

    /** 便利重载：夹具场景只关心一个模块目录，用同一份 canary 清单。 */
    private static List<String> staleCompiledOutputs(Path fixtureRoot) throws IOException {
        return staleCompiledOutputs(fixtureRoot, List.of(FIXTURE_CANARY));
    }

    // ───────────────────────────── 定位/IO 辅助 ─────────────────────────────

    private static Path locateFatJar() {
        String override = System.getProperty(PATH_FLAG);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return backendRoot().resolve("gateway/target/gateway-1.0.0-SNAPSHOT.jar");
    }

    /** 从 surefire cwd 上溯找到含 {@code ecos_backend/} 的仓库根。 */
    private static Path backendRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir.resolve("ecos_backend");
            }
            dir = dir.getParent();
        }
        throw new UncheckedIOException(new IOException(
                "Test run cwd 上溯未找到 ecos_backend/，无法定位 fat jar：" + Paths.get("").toAbsolutePath()));
    }

    private static Path m2Root() {
        return Paths.get(System.getProperty("user.home"), ".m2", "repository");
    }

    private static String findLibEntry(Path fatJar, String prefix) throws IOException {
        List<String> hit = new ArrayList<>();
        try (ZipFile zip = new ZipFile(fatJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(LIB_PREFIX + prefix) && name.endsWith(".jar")) {
                    hit.add(name.substring(LIB_PREFIX.length()));
                }
            }
        }
        if (hit.isEmpty()) {
            return null;
        }
        if (hit.size() > 1) {
            throw new IOException("内嵌 lib 命中多份 " + prefix + "：" + hit + "（唯一性腿会另行判红）");
        }
        return hit.get(0);
    }

    private static byte[] readBytesEntry(Path zip, String entryName) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zf.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static String readTextEntry(Path zip, String entryName) throws IOException {
        byte[] raw = readBytesEntry(zip, entryName);
        return raw == null ? null : new String(raw);
    }

    /** 从「jar 的字节」里取某 entry 的字节（嵌套 jar 用）。 */
    private static byte[] jarEntryBytes(byte[] jarBytes, String entryName) throws IOException {
        try (JarInputStream jin = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jin.getNextJarEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    jin.transferTo(bos);
                    return bos.toByteArray();
                }
            }
        }
        return null;
    }

    private static String stripVersion(String fileName) {
        String stem = fileName.substring(0, fileName.length() - ".jar".length());
        int idx = -1;
        for (int i = stem.length() - 1; i > 0; i--) {
            if (stem.charAt(i) == '-' && Character.isDigit(stem.charAt(i + 1))) {
                idx = i;
                break;
            }
        }
        return idx < 0 ? stem : stem.substring(0, idx);
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String head(String sha) {
        return sha.substring(0, 12);
    }

    // ───────────────────────────── 夹具构造 ─────────────────────────────

    /**
     * 造一棵与真实布局同形的最小树：{@code <root>/fat.jar} + {@code <root>/engine/.../target/{classes,jar}}
     * + {@code <root>/.m2/repository/com/chinacreator/gzcm/<artifact>/<version>/<artifact>-<version>.jar}。
     *
     * @param identical 内嵌 class 与模块/.m2 是否一致
     * @param dropCanary 是否从 fat jar 里整个去掉内嵌 jar（模拟「代码没打进产物」）
     */
    private static Path fixtureRoot(Path root, boolean identical, boolean dropCanary) throws IOException {
        Path module = root.resolve(FIXTURE_CANARY.moduleDir());
        Path classesDir = module.resolve("target/classes");
        Files.createDirectories(classesDir);
        Path entry = classesDir.resolve(FIXTURE_CANARY.entryClass());
        Files.createDirectories(entry.getParent());
        byte[] moduleClass = "module".getBytes();
        Files.write(entry, moduleClass);

        byte[] fatClass = identical ? moduleClass : "stale".getBytes();
        byte[] nestedJar = jarWithEntry(FIXTURE_CANARY.entryClass(), fatClass);
        Path moduleJar = module.resolve("target/kb-engine-impl-1.0.0-SNAPSHOT.jar");
        Files.write(moduleJar, nestedJar);

        Path m2Dir = root.resolve(".m2/repository/com/chinacreator/gzcm/kb-engine-impl/1.0.0-SNAPSHOT");
        Files.createDirectories(m2Dir);
        Files.write(m2Dir.resolve("kb-engine-impl-1.0.0-SNAPSHOT.jar"),
                jarWithEntry(FIXTURE_CANARY.entryClass(), moduleClass));

        Path fatJar = root.resolve("fat.jar");
        Files.createDirectories(fatJar.getParent());
        Map<String, byte[]> lib = new LinkedHashMap<>();
        if (!dropCanary) {
            lib.put(LIB_PREFIX + "kb-engine-impl-1.0.0-SNAPSHOT.jar", nestedJar);
        }
        lib.put(LIB_PREFIX + "runtime-core-1.0.0-SNAPSHOT.jar", jarWithEntry("com/example/Any.class", new byte[] {1}));
        writeZip(fatJar, lib);
        return root;
    }

    /** 时效夹具：可选把源码 mtime 设到 class 之后。 */
    private static Path freshSourceTree(Path root, boolean sourceNewer) throws IOException {
        Path module = root.resolve(FIXTURE_CANARY.moduleDir());
        Path classFile = module.resolve("target/classes").resolve(FIXTURE_CANARY.entryClass());
        Path sourceFile = module.resolve("src/main/java")
                .resolve(FIXTURE_CANARY.entryClass().replace(".class", ".java"));
        Files.createDirectories(classFile.getParent());
        Files.createDirectories(sourceFile.getParent());
        Files.write(classFile, new byte[] {1});
        Files.write(sourceFile, "class Foo {}".getBytes());
        if (sourceNewer) {
            Files.setLastModifiedTime(classFile, java.nio.file.attribute.FileTime.fromMillis(1_000));
            Files.setLastModifiedTime(sourceFile, java.nio.file.attribute.FileTime.fromMillis(2_000));
        } else {
            Files.setLastModifiedTime(classFile, java.nio.file.attribute.FileTime.fromMillis(2_000));
            Files.setLastModifiedTime(sourceFile, java.nio.file.attribute.FileTime.fromMillis(1_000));
        }
        return root;
    }

    private static Path twoVersionLibFatJar(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        Map<String, byte[]> lib = new LinkedHashMap<>();
        lib.put(LIB_PREFIX + "kb-engine-impl-1.0.0-SNAPSHOT.jar", jarWithEntry("com/example/A.class", new byte[] {1}));
        lib.put(LIB_PREFIX + "kb-engine-impl-0.9.0-SNAPSHOT.jar", jarWithEntry("com/example/A.class", new byte[] {2}));
        writeZip(jar, lib);
        return jar;
    }

    private static byte[] jarWithEntry(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(bos)) {
            jos.putNextEntry(new JarEntry(name));
            jos.write(content);
            jos.closeEntry();
        }
        return bos.toByteArray();
    }

    private static void writeZip(Path jar, Map<String, byte[]> entries) throws IOException {
        try (var out = Files.newOutputStream(jar);
             var zos = new java.util.zip.ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
    }
}
