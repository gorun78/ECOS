package com.chinacreator.gzcm.engine.data;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ARCH-06 (F02-13) —— 业务代码禁 {@code java.sql.DriverManager}，合法白名单 = {@code runtime-access/**}。
 *
 * <p>建连必须收敛 runtime-access 的 {@code JdbcConnector}（铁律 #4）。data-engine 负责数据源
 * 与 SQL 控制台业务，历史上散落的 {@code DriverManager} 直连（含 {@code BaseJdbcAdapter.connect}）
 * 已全部改经 {@code JdbcAccessBridge} → {@code JdbcConnector}。本测试双层守护：
 * <ol>
 *   <li>字节码层（ArchUnit）：data-engine 任何类不得依赖 {@code java.sql.DriverManager}；</li>
 *   <li>文本层：data-engine-impl/src/main 出现 {@code import java.sql.DriverManager} 或
 *       {@code DriverManager.getConnection / .registerDriver} 即 fail（bytecode 对静态工具类
 *       偶发 import-without-use 漏网，文本层是权威护栏，见 RlsInjectionGuardArchTest 先例）。</li>
 * </ol>
 */
public class NoDriverManagerArchTest {

    private static JavaClasses dataEngineClasses;

    @BeforeAll
    static void setUp() {
        dataEngineClasses = new ClassFileImporter()
            .importPackages("com.chinacreator.gzcm.engine.data");
    }

    /** 字节码层：禁依赖 java.sql.DriverManager。 */
    @Test
    void dataEngineMustNotDependOnJdbcDriverManager() {
        noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.data..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("java.sql.DriverManager")
            .check(dataEngineClasses);
    }

    /** 文本层：src/main 禁 import / 调用 DriverManager。 */
    @Test
    void dataEngineSourcesMustNotReferenceDriverManager() throws IOException {
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Pattern use = Pattern.compile("DriverManager\\.(getConnection|registerDriver|deregisterDriver)");
        Pattern imp = Pattern.compile("import\\s+java\\.sql\\.DriverManager\\s*;");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .sorted()
                    ::iterator) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (use.matcher(text).find() || imp.matcher(text).find()) {
                    hits.add(p.toString());
                }
            }
        }
        assertTrue(hits.isEmpty(),
            "ARCH-06 违规 —— 数据域业务代码直用 java.sql.DriverManager（应经 runtime-access JdbcConnector）:\n"
                + String.join("\n", hits));
    }
}
