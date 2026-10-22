package com.chinacreator.gzcm.runtime.access.connector;

import com.chinacreator.gzcm.common.data.model.ColumnMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 底座<b>列面</b>行为门（#142/B4-3e-46，收口差距 <b>D-B4-76</b> 的底座腿）。
 *
 * <p><b>本门为什么必须存在</b>：{@code Connector} 接口此前只有 {@code queryPreview}
 * （给样本<b>行</b>），承流面拿不到「列名／类型／可空／备注」，于是前端 schema 预览要么读空、
 * 要么由上层猜一个 {@code varchar}。本批在底座新增 {@code listColumns}，其契约的核心不是
 * 「取到列」而是<b>「取不到时怎么说」</b>：空集合＝确实无列、{@code null} 属性＝该属性未知、
 * {@link UnsupportedOperationException}＝该类型没有这一面。三者若被折叠成同一形态，
 * 上层就无法分态（§4.7.2 与 #142/B4-3e-44 同一口径）。本门逐一钉住三态。</p>
 *
 * <p>覆盖实跑面：CSV 真读临时文件（含无表头回落 {@code C1..Cn}）、REST 显式不可问、
 * 三枚实现类全部实现该方法（防「接口加了、实现类漏了」的编译期外盲区由反射见证兜住）。
 * JDBC 真连库腿不在本门范围（无测试库夹具），其类型／可空／备注取数由
 * {@code JdbcConnector#listColumns} 源码锚点与属主侧降级门承担，此处不宣称已实跑覆盖。</p>
 */
class ConnectorColumnSurfaceTest {

    private final CsvConnector csv = new CsvConnector();
    private final RestApiConnector rest = new RestApiConnector();

    @Test
    @DisplayName("CSV 有表头：列名逐枚透出，类型/可空/备注一律 null＝未知（禁猜 varchar）")
    void csvHeaderColumnsCarryNamesOnly(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("orders.csv");
        Files.writeString(file, "order_id,amount,remark\n1,9.90,x\n", StandardCharsets.UTF_8);

        List<ColumnMetadata> columns =
                csv.listColumns("{\"filePath\":\"" + jsonPath(file) + "\",\"hasHeader\":true}", "orders");

        assertEquals(3, columns.size(), "列面数量＝表头字段数");
        assertEquals(List.of("order_id", "amount", "remark"), columns.stream()
                .map(ColumnMetadata::getName).toList());
        for (ColumnMetadata column : columns) {
            assertNull(column.getTypeName(), "文本文件没有类型概念，typeName 必须留 null 而非 \"varchar\"");
            assertNull(column.getRequired(), "可空性未知，必须留 null 而非 false");
            assertNull(column.getComment(), "备注未知，必须留 null 而非空串");
        }
    }

    @Test
    @DisplayName("CSV 无表头：列面回落 C1..Cn，与 readRows 的列名口径一致")
    void csvWithoutHeaderFallsBackToGeneratedNames(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.csv");
        Files.writeString(file, "a,b,c\n", StandardCharsets.UTF_8);

        List<ColumnMetadata> columns =
                csv.listColumns("{\"filePath\":\"" + jsonPath(file) + "\"}", "plain");

        assertEquals(List.of("C1", "C2", "C3"),
                columns.stream().map(ColumnMetadata::getName).toList());
    }

    @Test
    @DisplayName("CSV 缺 filePath／文件不可读：显式抛错，不得折成空列面")
    void csvFailureLegsThrowInsteadOfReturningEmptyList(@TempDir Path dir) {
        RuntimeException missingConfig = assertThrows(RuntimeException.class,
                () -> csv.listColumns("{}", "any"));
        assertTrue(missingConfig.getMessage().contains("filePath"),
                "原因要指向缺失的配置键，实际: " + missingConfig.getMessage());

        RuntimeException missingFile = assertThrows(RuntimeException.class, () -> csv.listColumns(
                "{\"filePath\":\"" + jsonPath(dir.resolve("nope.csv")) + "\"}", "any"));
        assertTrue(missingFile.getMessage().contains("不存在") || missingFile.getMessage().contains("不可读"),
                "实际: " + missingFile.getMessage());
    }

    @Test
    @DisplayName("REST 无声明式列面：抛 UnsupportedOperationException，不返回空集合")
    void restApiDeclaresTheSurfaceAsNotAskable() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> rest.listColumns("{\"baseUrl\":\"http://127.0.0.1:1/api\"}", "any"));
        assertTrue(thrown.getMessage().contains("列面"),
                "不可问的理由要写进消息，供属主侧 fieldsReason 透出，实际: " + thrown.getMessage());
    }

    @Test
    @DisplayName("接口新增列面后，三枚连接器实现类必须各自见证实现（防漏实现只靠编译期兜）")
    void everyConnectorImplementationDeclaresListColumns() {
        // 反射判据自检：getMethod 对确实不存在的方法必须抛错，否则「见证」这条恒真。
        assertThrows(NoSuchMethodException.class, () -> CsvConnector.class.getMethod("noSuchMethod"));
        List<Class<?>> implementations = List.of(CsvConnector.class, RestApiConnector.class,
                JdbcConnector.class);
        for (Class<?> type : implementations) {
            try {
                var method = type.getMethod("listColumns", String.class, String.class);
                assertEquals(type, method.getDeclaringClass(),
                        type.getSimpleName() + " 未自己声明 listColumns（继承默认实现＝该面未接）");
            } catch (NoSuchMethodException e) {
                throw new AssertionError(type.getSimpleName() + " 缺少 listColumns(String, String)", e);
            }
        }
    }

    /** Windows 路径反斜杠要转义进 JSON 串，否则解析失败（假失败会被读成「通道不可用」）。 */
    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
