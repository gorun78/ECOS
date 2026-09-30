package com.chinacreator.gzcm.engine.data.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-01（W52 数据域）验收《DatasourceSingleSurfaceArchTest》。
 *
 * <p>设计 §二验收：源码断言 {@code /api/v1/datanet/datasource}（{@code DataSourceController}）
 * 与 {@code /api/v1/datasource}（{@code PMO45DataSourceController}）两条外部路径的
 * handler 均委托<b>同一 service</b>（单套 CRUD、禁两份 SQL）。</p>
 *
 * <p>实现为纯源码扫描（Files.readString），不依赖运行 Spring 上下文 / classloader：
 * <ol>
 *   <li>从 {@code user.dir} 逐级上溯找到含 {@code .git} 的仓库根，再定位两个 Controller 源文件；</li>
 *   <li>对每个 Controller 用正则抽取其「主 service 字段」类型（构造注入的那个 Service 类型名）；</li>
 *   <li>断言二者字段类型字符串<b>相等</b> = 同一 service 接口/类。</li>
 * </ol></p>
 */
@DisplayName("F02-01 单套 CRUD 收口（双路径同 service）架构守护")
class DatasourceSingleSurfaceArchTest {

    private static final String ECMECOS_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataSourceController.java";
    private static final String PMO45_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/PMO45DataSourceController.java";

    /** 主 service 字段：构造器首个注入的、名字以 Service 结尾的类型（DataSourceRegistryService）。 */
    private static final Pattern FIELD_SERVICE =
            Pattern.compile("private\\s+final\\s+(\\w+Service)\\s+\\w+\\s*;");

    @Test
    @DisplayName("两路径 Controller 的 primary service 字段类型字符串一致（= 同一委托 service）")
    void bothControllersDelegateToSameServiceType() throws IOException {
        Path repoRoot = findRepoRoot();
        String ds = Files.readString(repoRoot.resolve(ECMECOS_REL), StandardCharsets.UTF_8);
        String pmo = Files.readString(repoRoot.resolve(PMO45_REL), StandardCharsets.UTF_8);

        String dsType = firstServiceFieldType(ds);
        String pmoType = firstServiceFieldType(pmo);

        assertNotNull(dsType, "DataSourceController 未抽到主 service 字段：应为 DataSourceRegistryService");
        assertNotNull(pmoType, "PMO45DataSourceController 未抽到主 service 字段：应为 DataSourceRegistryService");

        assertEquals(dsType, pmoType,
                "两路径必须委托同一 service 类型（单套 CRUD，禁两份 SQL）：\n  DataSourceController=" + dsType
                        + "\n  PMO45DataSourceController=" + pmoType);

        // 收口到 com.chinacreator 的 DataSourceRegistryService，而非两份各自 impl
        assertEquals("DataSourceRegistryService", dsType,
                "主承流面应收口到 DataSourceRegistryService（对 DataSourceServiceImpl 的薄封装）");
    }

    @Test
    @DisplayName("两 Controller 路径映射分别指向 /datanet/datasource 与 /datasource（别名保留）")
    void pathsMatchSpec() throws IOException {
        Path repoRoot = findRepoRoot();
        String ds = Files.readString(repoRoot.resolve(ECMECOS_REL), StandardCharsets.UTF_8);
        String pmo = Files.readString(repoRoot.resolve(PMO45_REL), StandardCharsets.UTF_8);

        assertTrue(ds.contains("/datanet/datasource"),
                "DataSourceController 必须映射 /datanet/datasource（唯一承流面）");
        assertTrue(pmo.contains("/datasource"),
                "PMO45DataSourceController 必须映射 /datasource（兼容别名）");
    }

    @Test
    @DisplayName("两 Controller 均不新建第二套 SQL（不各自 new JdbcTemplate）= 单套 CRUD 前端口径无漂移")
    void neitherControllerOwnsJdbc() throws IOException {
        Path repoRoot = findRepoRoot();
        String ds = Files.readString(repoRoot.resolve(ECMECOS_REL), StandardCharsets.UTF_8);
        String pmo = Files.readString(repoRoot.resolve(PMO45_REL), StandardCharsets.UTF_8);

        assertFalse(ds.contains("JdbcTemplate"), "Controller 层禁止直用 JdbcTemplate（单套 SQL 在 service）");
        assertFalse(pmo.contains("JdbcTemplate"), "Controller 层禁止直用 JdbcTemplate（单套 SQL 在 service）");
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    /** 从 user.dir 逐级上溯找到仓库根（含 .git 目录或 .git 文件）。 */
    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        // 兜底：按 pom.xml 上溯（monorepo 根存在 pom.xml）
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }

    private static String firstServiceFieldType(String source) {
        Matcher m = FIELD_SERVICE.matcher(source);
        return m.find() ? m.group(1) : null;
    }
}
