package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-06 规格 4 验收（PRD-05 §6.1 末条）：附件 §16.6 C 类型白名单与 PRD-04 REQ-KB-06
 * K 类型白名单<b>互斥且同源校验</b>。
 *
 * <p>冻结标识：{@code mvn -Dtest=KCBoundaryWhitelistTest#cAndKTypeSetsAreDisjoint}。</p>
 *
 * <p><b>基准选取（诚实说明）</b>：附件一册 §5 的"K 12 类"原文未入库，且 PRD-04 §6.1 明确
 * "12 类为候选集、是否全数采纳随 R-46（本册不预设已批准）"⇒ 仓内<b>不存在</b>可对的 K 类型代码单源。
 * 本测试因此以<b>仓内 K 层权威载体单源</b>（迁移脚本中 {@code ecos_knowledge} schema 的建表语句）
 * 反推 K 对象名集，并另证更强的两条：C 物理载体零寄生 {@code ecos_knowledge}、C 类型名与 K 表名去前缀后不相交。
 * R-46 落定 K 白名单入码后，本测试的 K 集应改读该代码单源（届时只换数据源、断言不变）。</p>
 */
@DisplayName("K/C 类型白名单互斥（REQ-COG-06 规格 4）")
class KCBoundaryWhitelistTest {

    private static final Pattern KB_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?ecos_knowledge\\.([a-z0-9_]+)");

    /** knownLegacy 前缀（V47 隔离期遗留形态），去前缀后与 ecos_kb_* 同名 ⇒ 归并避免假冲突。 */
    private static final List<String> PREFIXES = List.of("ecos_kb_", "ecos_kg_", "kb_", "kg_");

    @Test
    @DisplayName("C 11 类对象名与 K 层载体名去前缀后不相交，且 C 载体零寄生 ecos_knowledge")
    void cAndKTypeSetsAreDisjoint() throws IOException {
        Set<String> kTypes = readKLayerTypes();
        assertTrue(kTypes.size() >= 5, "K 层载体名集异常小（" + kTypes + "），单源目录可能漂移");

        Set<String> cTypes = new LinkedHashSet<>(CognitiveObjectClass.objectKeys());
        assertEquals(11, cTypes.size(), "C 白名单是封闭枚举，条目数即附件冻结数（防膨胀钩子）");

        List<String> collisions = new ArrayList<>();
        for (String c : cTypes) {
            for (String k : kTypes) {
                if (k.equals(c) || k.endsWith("_" + c) && !"belief".equals(c)) {
                    collisions.add(c + " <-> ecos_knowledge." + k);
                }
            }
        }
        assertEquals(List.of(), collisions, "同一类型名不得同时出现在 K 与 C 白名单（PRD-04 §6.1 规格 3 同向）");

        for (String carrier : CognitiveObjectClass.physicalCarriers()) {
            String schema = carrier.substring(0, carrier.indexOf('.')).toLowerCase(Locale.ROOT);
            assertTrue(!"ecos_knowledge".equals(schema), "C 对象寄生 K 层：" + carrier);
            assertTrue(!"ecos_ai".equals(schema), "C 对象复制进 W 控制面（ADR-16/17）：" + carrier);
        }
    }

    /** 从迁移脚本单源目录读 K 层表名并去前缀。 */
    private Set<String> readKLayerTypes() throws IOException {
        Path dir = CogScope.REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
        assertTrue(Files.isDirectory(dir), "迁移单源目录不存在：" + dir);
        Set<String> types = new LinkedHashSet<>();
        try (var walk = Files.walk(dir, 1)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher m = KB_TABLE.matcher(readUtf8(p));
                while (m.find()) {
                    types.add(stripPrefix(m.group(1).toLowerCase(Locale.ROOT)));
                }
            }
        }
        return types;
    }

    private static String stripPrefix(String table) {
        for (String prefix : PREFIXES) {
            if (table.startsWith(prefix)) {
                return table.substring(prefix.length());
            }
        }
        return table;
    }

    /** 宽松 UTF-8 读（存量 buszhi/sysman impl 的 pom.xml 非 UTF-8，严格解码会抛 MalformedInputException 使门禁假崩）。 */
    private static String readUtf8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

}
