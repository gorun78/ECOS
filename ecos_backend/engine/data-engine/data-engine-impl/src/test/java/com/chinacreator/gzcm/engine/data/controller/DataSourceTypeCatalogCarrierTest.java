package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import com.chinacreator.gzcm.runtime.access.connector.DataSourceTypeVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataSourceTypeCatalogCarrierTest — 数据源类型目录的承流侧取证（#142/B4-3e-64，D-B4-78 前端侧腿的属主出口）。
 *
 * <p><b>本门盯的病灶</b>：改前「可登记哪些数据源」有三个各自演化的面——服务准入面
 * {@code DataSourceServiceImpl.SUPPORTED_TYPES}（2026-10-25 收口 10 枚、#142/B4-3e-71
 * 起 DORIS 接承流扩 11 枚）、工厂别名面（含 {@code SQLSERVER}/{@code GAUSSDB}
 * 等异名）、前端下拉面 {@code CONNECTION_TYPES}（19 枚，含 9 枚后端根本不承流的幻影选项，即 D-B4-78
 * 的用户面读数：选了只能撞 400）。B4-3e-63 把前两枚归一到属主枚举
 * {@link DataSourceTypeVocabulary}；本批补的是<b>承流出口</b>：前端改读
 * {@code GET /api/v1/datanet/datasource/supported-types}，故必须钉住
 * ①出口在唯一承流面上（不是 F02-01 判定的兼容别名面 {@code /api/v1/datasource}）、
 * ②目录成员恒等于服务准入面、③键面契约恰两键且顺序稳定。</p>
 *
 * <p><b>为什么禁「解析不到就少一枚」</b>：{@code registrable} 成员缺规范名／缺别名表属词表被改坏，
 * 若目录构造在此静默跳过，用户面读数就是「前端下拉少一枚而日志无痕」——与本批要防的两面各自演化同型。
 * 该数据缺陷腿由 {@link #dataDefectLegsThrowNamed} 用夹具直调证明<b>能变红</b>（不靠改生产码取证）。</p>
 *
 * <p><b>判据失明防护</b>：冻结表按「枚举声明序 + 枚名」双重钉值，且源码扫描要求目录构造只遍历
 * {@code DataSourceTypeVocabulary.values()} 并按 {@code registrable()} 过滤、不得出现任何类型名硬编码——
 * 一旦有人在本类手抄第二份表（D-B4-78 的原始形态），即使 11 枚数值仍对得上也会变红。</p>
 */
@DisplayName("#142/B4-3e-64 数据源类型目录承流出口（单源＋恰两键＋禁第二份表）")
class DataSourceTypeCatalogCarrierTest {

    private static final String CONTROLLER_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataSourceController.java";

    /** 承流面类级路径——唯一允许出现 {@code supported-types} 出口的族根（F02-01 + DatasourceSingleSurfaceArchTest 口径）。 */
    private static final String CARRIER_PATH = "/api/v1/datanet/datasource";

    /** 兼容别名面路径——本批不得把新出口绑到这里（别名面约定 ≥2 迭代后删除，绑上即制造新依赖面）。 */
    private static final String ALIAS_PATH = "/api/v1/datasource";

    /**
     * 冻结表：{@code registrable} 成员的枚举声明序（2026-10-25 自
     * {@link DataSourceTypeVocabulary} 逐枚读出，非手抄）。任何扩面／改序都要在此显式订正，
     * 并与前端 {@code deriveSupportedOptions} 的裁剪结果一起过门。
     *
     * <p>#142/B4-3e-71：被拒闭集 9 枚逐枚定档，DORIS 判「接承流」——末位追加第 11 枚
     * {@code DORIS}（声明序上它排在未裁组首位，恰好落在 registrable 面尾）。其余 8 枚
     * （s3/oss/csv/sftp/kafka/mongodb/sap/rest_api）保留显示面、永不入业务准入，本表不扩。</p>
     */
    private static final List<String> REGISTRABLE_ORDER = List.of(
            "POSTGRESQL", "MYSQL", "ORACLE", "MSSQL", "DM", "KINGBASE", "GAUSS",
            "CLICKHOUSE", "MINIO", "FILESYSTEM", "DORIS");

    // ── ① 目录成员＝服务准入面（禁第二份表，运行期对账）──────────────────

    @Test
    @DisplayName("目录成员与顺序恒等于词表 registrable 声明序（11 枚冻结）")
    void catalogIsRegistrableInDeclarationOrder() {
        List<Map<String, Object>> catalog = DataSourceController.registrableTypeCatalog();

        assertEquals(REGISTRABLE_ORDER.size(), catalog.size(),
                "准入面成员数变动须逐枚订正冻结表（#142/B4-3e-71 已扩准入：DORIS 接承流入第 11 枚）");

        for (int i = 0; i < REGISTRABLE_ORDER.size(); i++) {
            assertEquals(REGISTRABLE_ORDER.get(i), catalog.get(i).get("canonical"),
                    "第 " + (i + 1) + " 枚 canonical 与词表声明序不符（前端下拉按本序渲染）");
        }
    }

    @Test
    @DisplayName("目录 canonical 多重集＝服务准入面 SUPPORTED_TYPES（同一来源，零对账缺口）")
    void catalogMatchesServiceAcceptanceFace() {
        Set<String> fromCatalog = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : DataSourceController.registrableTypeCatalog()) {
            fromCatalog.add((String) row.get("canonical"));
        }
        assertEquals(DataSourceServiceImpl.SUPPORTED_TYPES, fromCatalog,
                "「下拉可选」必须与「后端能登」恒等——两者皆由 DataSourceTypeVocabulary.registrable 派生");
        assertEquals(REGISTRABLE_ORDER.size(), DataSourceServiceImpl.SUPPORTED_TYPES.size(),
                "服务准入面自身也不得与冻结表脱钩");
    }

    @Test
    @DisplayName("每枚恰两键 {canonical, aliases}；aliases 为数组（可空，禁 null）")
    void catalogRowsHaveExactlyTwoKeys() {
        for (Map<String, Object> row : DataSourceController.registrableTypeCatalog()) {
            String who = String.valueOf(row.get("canonical"));
            assertEquals(Set.of("canonical", "aliases"), row.keySet(),
                    "键面契约只有两键（前端唯一解析依据）：" + who);
            Object aliases = row.get("aliases");
            assertNotNull(aliases, "aliases 不得为 null（空面的语义由属主显式给空数组）：" + who);
            assertTrue(aliases instanceof List<?>, "aliases 须为数组形态：" + who);
        }
    }

    @Test
    @DisplayName("同物异名走 aliases 承载（GAUSSDB→GAUSS、SQLSERVER→MSSQL），前端靠它做显示归一")
    void aliasFaceCarriesKnownDrift() {
        Map<String, Object> gauss = rowOf("GAUSS");
        assertTrue(asStringList(gauss.get("aliases")).contains("GAUSSDB"),
                "D-B4-78 的直接病灶＝前端历史名 gaussdb：须以别名指向词表成员，而非新增一枚类型");
        Map<String, Object> mssql = rowOf("MSSQL");
        assertTrue(asStringList(mssql.get("aliases")).contains("SQLSERVER"),
                "收口前工厂别名表里 SQLSERVER/MSSQL 两条键各自存在，本批定为同一枚的两名");
        assertTrue(asStringList(rowOf("POSTGRESQL").get("aliases")).isEmpty(),
                "无同物异名者给空数组，不得缺键、不得落 null");
    }

    // ── ② 出口在承流面上（而非兼容别名面）──────────────────────────────

    @Test
    @DisplayName("supported-types 出口挂在唯一承流面，类级路径不含别名面 /api/v1/datasource")
    void endpointSitsOnCarrierSurface() throws IOException {
        String src = Files.readString(repoRoot().resolve(CONTROLLER_REL), StandardCharsets.UTF_8);

        Pattern mapping = Pattern.compile("@RequestMapping\\(\\{([^}]*)\\}\\)");
        Matcher m = mapping.matcher(src);
        assertTrue(m.find(), "类级 @RequestMapping 未被解析到＝判据失明，本门不得静默通过");
        String classPaths = m.group(1);
        assertTrue(classPaths.contains(CARRIER_PATH),
                "承流面类级路径须含 " + CARRIER_PATH + "，实得：" + classPaths);
        assertFalse(classPaths.contains(ALIAS_PATH),
                "本出口不得挂在兼容别名面上（别名面由 PMO45DataSourceController 承接，只增不改、不在本批扩能）");

        assertTrue(src.contains("@GetMapping(\"/supported-types\")"),
                "出口路径须逐字为 /supported-types（前端族根拼接的单源契约）");
    }

    @Test
    @DisplayName("目录构造只遍历词表、零类型名硬编码（禁在本类手抄第二份表）")
    void catalogBuilderHasNoSecondTable() throws IOException {
        String src = Files.readString(repoRoot().resolve(CONTROLLER_REL), StandardCharsets.UTF_8);
        String body = extractMethod(src, "registrableTypeCatalog");
        assertTrue(body.contains("DataSourceTypeVocabulary.values()"),
                "目录须由属主枚举派生，实得：" + body);
        assertTrue(body.contains(".registrable()"),
                "目录须按准入判据过滤，不得自定成员集合");
        for (String name : REGISTRABLE_ORDER) {
            assertFalse(body.contains("\"" + name + "\""),
                    "registrableTypeCatalog 内出现类型名字面量 " + name + "＝第二份表回潮（D-B4-78 原始形态）");
        }
    }

    // ── ③ 运行期成功腿：显式 200 ＋ 信封 code=0 ＋ 目录在位 ──────────────

    @Test
    @DisplayName("supportedTypes() 显式 200＋成功信封＋11 枚（纯内存枚举遍历，无通道失败腿）")
    void endpointReturnsCarrierEnvelope() {
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> resp =
                new DataSourceController(null).supportedTypes();

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "成功腿须显式 200，不得靠裸回折叠");
        ApiResponse<List<Map<String, Object>>> body = resp.getBody();
        assertNotNull(body, "响应体须在位");
        assertTrue(body.isSuccess(), "成功腿信封 code 须为成功码，实得：" + body.getCode());
        assertEquals(REGISTRABLE_ORDER.size(), body.getData().size(), "承流面出参成员数须与冻结表一致");
    }

    // ── ④ 数据缺陷腿能变红（合成夹具直调，不改生产码取证）────────────────

    @Test
    @DisplayName("夹具：registrable 成员缺规范名／别名表为 null ⇒ 显式抛出并带枚名")
    void dataDefectLegsThrowNamed() {
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> DataSourceController.typeCatalogRow("DORIS", "  ", List.of()),
                "可登记成员规范名为空白必须显式失败，不得折成「少一枚」");
        assertTrue(blank.getMessage().contains("DORIS"),
                "抛出消息须带枚名以便定位，实得：" + blank.getMessage());

        IllegalStateException nulls = assertThrows(IllegalStateException.class,
                () -> DataSourceController.typeCatalogRow("DORIS", "DORIS", null),
                "aliases 为 null 属契约违约（要求数组），必须显式失败");
        assertTrue(nulls.getMessage().contains("DORIS"),
                "抛出消息须带枚名，实得：" + nulls.getMessage());
    }

    @Test
    @DisplayName("夹具：合法入参出恰两键（证明 ④ 的两条红字来自入参形状而非门本身恒红）")
    void happyPathFixtureProvesTheRedLegsAreInputShaped() {
        Map<String, Object> row = DataSourceController.typeCatalogRow("DORIS", "DORIS", List.of());
        assertEquals(Set.of("canonical", "aliases"), row.keySet());
        assertEquals("DORIS", row.get("canonical"));
        assertEquals(List.of(), row.get("aliases"));
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private static Map<String, Object> rowOf(String canonical) {
        return DataSourceController.registrableTypeCatalog().stream()
                .filter(r -> canonical.equals(r.get("canonical")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("目录内找不到 " + canonical
                        + "——冻结表与目录构造已脱钩"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        return (List<String>) value;
    }

    /** 抽取方法体（到配对右括号止）：只用于源码扫描判据，找不到即红＝判据失明不空过。 */
    private static String extractMethod(String src, String methodName) {
        int sig = src.indexOf("static List<Map<String, Object>> " + methodName + "()");
        assertTrue(sig >= 0, "未定位到 " + methodName + " 方法签名＝解析失明，须显式订正判据");
        int brace = src.indexOf('{', sig);
        assertTrue(brace > 0, methodName + " 未找到方法体起点");
        int depth = 0;
        for (int i = brace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(brace, i + 1);
                }
            }
        }
        throw new AssertionError(methodName + " 方法体括号不配对，判据无法定界");
    }

    private static Path repoRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git")) || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir 定位仓库根（.git 缺失）");
    }
}
