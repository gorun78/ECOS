package com.chinacreator.gzcm.engine.data.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.model.ColumnMetadata;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.dao.DataSourceDao;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 属主<b>服务侧</b>原始配置预览通道的行为门（#142/B4-3e-46，收口差距 <b>D-B4-76</b> 的服务腿）。
 *
 * <p>本门直接构造<b>真服务</b>（dao / ConnectorFactory / Connector 为 mock），钉住四件契约：
 * 键面单源（{@code tableNames}/{@code fields}/{@code fieldsObject}/{@code fieldsDegraded}）、
 * 不可用与空结果分态（§4.7.2）、列面失败只声明部分可用而不作废已取到的对象面、
 * 底座给不出的列属性逐枚留 {@code null} 而不猜值。</p>
 *
 * <p>端点侧状态行保真另见 {@code DataSourcePreviewByConfigStatusFidelityTest}；
 * 两门与 #142/B4-3e-44 的「真服务＋mock」/「mock 服务＋真端点」配对口径一致，
 * 单看任一门都不足以宣称通道已收口。</p>
 */
class DataSourcePreviewByConfigCarriedThrowTest {

    private final DataSourceDao dao = mock(DataSourceDao.class);
    private final ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
    private final Connector connector = mock(Connector.class);
    private final DataSourceServiceImpl service = new DataSourceServiceImpl(dao, connectorFactory);

    /** enterprise 档夹具：MINIO 类型只在 enterprise/ultimate 可用，standard 下会被类型校验拒掉。 */
    private DataSourceServiceImpl enterpriseService() {
        service.setSpringEnv(envWithProfile("enterprise"));
        return service;
    }

    private static Environment envWithProfile(String profile) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[] {profile});
        return env;
    }

    private void connectorFor(String type) {
        when(connectorFactory.getConnector(type)).thenReturn(connector);
    }

    private static DataResource resource(String sourcePath, String resourceName) {
        DataResource r = new DataResource();
        r.setSourcePath(sourcePath);
        r.setResourceName(resourceName);
        return r;
    }

    @Test
    @DisplayName("关系型成功腿：tableNames 与 fields 键面齐全，fieldsObject 指明列面归属")
    void jdbcShapeLegReturnsTableNamesAndFields() {
        connectorFor("MYSQL");
        when(connector.listResources(anyString(), any(), any()))
                .thenReturn(List.of(resource("db.orders", null), resource(null, "db.users")));
        when(connector.listColumns(anyString(), ArgumentMatchers.eq("db.orders"))).thenReturn(List.of(
                new ColumnMetadata("id", "INT", Boolean.TRUE, "主键"),
                new ColumnMetadata("note", null, null, null)));

        Map<String, Object> result = service.previewSchemaByConfig(
                "MYSQL", "{\"jdbcUrl\":\"jdbc:mysql://127.0.0.1:3306/db\"}");

        assertEquals("MYSQL", result.get("type"));
        assertEquals(List.of("db.orders", "db.users"), result.get("tableNames"),
                "取名口径与 previewSchema 一致：sourcePath 优先、回落 resourceName");
        assertEquals("db.orders", result.get("fieldsObject"),
                "列面属于哪张对象必须显式透出，禁让前端把归属读丢");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("id", fields.get(0).get("name"));
        assertEquals("INT", fields.get(0).get("type"));
        assertEquals(Boolean.TRUE, fields.get(0).get("required"));
        assertEquals("主键", fields.get(0).get("comment"));
        assertNull(fields.get(1).get("type"),
                "底座未知属性必须逐列留 null，禁折叠成 varchar（D-B4-76 的读空根源之一就是上层猜值）");
        assertNull(fields.get(1).get("required"));
        assertFalse(result.containsKey("fieldsDegraded"),
                "列面取到时就不得附带降级声明");
        assertFalse(result.containsKey("note"),
                "note 只属于未实现的占位面，成功腿不带＝键面可判别");
    }

    @Test
    @DisplayName("底座取不到对象面：抛携带 503＋ECOS-DATA-051，禁返回「0 个表」的成功体")
    void unavailableConnectorLegThrowsCarried503() {
        connectorFor("MYSQL");
        when(connector.listResources(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Communications link failure"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> service.previewSchemaByConfig("MYSQL", "{\"jdbcUrl\":\"x\"}"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), thrown.getHttpStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), thrown.getErrorCode(),
                "§3.2.7 同号性：状态行与信封码同源");
        assertTrue(thrown.getMessage().startsWith(DataDomainErrorCode.ECOS_DATA_051),
                "实际: " + thrown.getMessage());
        assertNotNull(thrown.getCause(), "原始原因随 cause 保留");
    }

    @Test
    @DisplayName("类型在白名单内但无连接器 bean：按不可用（503）上报，不折成入参 400")
    void missingConnectorBeanIsAssemblyFaultNotBadRequest() {
        // 与 ConnectorFactory#getConnector 实况同型：白名单内却找不到 bean ⇒ IllegalArgumentException。
        when(connectorFactory.getConnector(anyString()))
                .thenThrow(new IllegalArgumentException("No connector bean found for type: MYSQL"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> service.previewSchemaByConfig("MYSQL", "{\"jdbcUrl\":\"x\"}"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), thrown.getHttpStatus());
        assertTrue(thrown.getMessage().startsWith(DataDomainErrorCode.ECOS_DATA_051));
    }

    @Test
    @DisplayName("对象面已取到、列面腿失败：整单不作废，改以 fieldsDegraded＋原因显式声明")
    void columnLegFailureKeepsTableNamesAndDeclaresPartialAvailability() {
        connectorFor("MYSQL");
        when(connector.listResources(anyString(), any(), any()))
                .thenReturn(List.of(resource("db.orders", null)));
        when(connector.listColumns(anyString(), anyString()))
                .thenThrow(new RuntimeException("column privilege denied"));

        Map<String, Object> result = service.previewSchemaByConfig("MYSQL", "{\"jdbcUrl\":\"x\"}");

        assertEquals(List.of("db.orders"), result.get("tableNames"),
                "列面失败不能把已成立的对象面一并作废（过度失败＝另一种失真）");
        assertEquals(Boolean.TRUE, result.get("fieldsDegraded"));
        assertEquals("column privilege denied", result.get("fieldsReason"));
        assertEquals(List.of(), result.get("fields"), "降级腿的列面为空集合，且与不可用靠布尔键分态");
    }

    @Test
    @DisplayName("该类型无声明式列面（REST）：原因写明「不可问」，与「取列失败」分态")
    void notAskableSurfaceIsDeclaredSeparatelyFromFailure() {
        connectorFor("POSTGRESQL");
        when(connector.listResources(anyString(), any(), any()))
                .thenReturn(List.of(resource("endpoint", null)));
        when(connector.listColumns(anyString(), anyString()))
                .thenThrow(new UnsupportedOperationException("REST 数据源不提供声明式列面"));

        Map<String, Object> result = service.previewSchemaByConfig("POSTGRESQL", "{\"jdbcUrl\":\"x\"}");

        assertEquals(Boolean.TRUE, result.get("fieldsDegraded"));
        assertTrue(String.valueOf(result.get("fieldsReason")).startsWith("该数据源类型不提供声明式列面"),
                "实际: " + result.get("fieldsReason"));
    }

    @Test
    @DisplayName("对象面确实为空：空 tableNames 且不带 fieldsDegraded（空 ≠ 失败）")
    void emptyObjectSurfaceStaysDistinguishableFromFailure() {
        connectorFor("MYSQL");
        when(connector.listResources(anyString(), any(), any())).thenReturn(List.of());

        Map<String, Object> result = service.previewSchemaByConfig("MYSQL", "{\"jdbcUrl\":\"x\"}");

        assertEquals(List.of(), result.get("tableNames"));
        assertFalse(result.containsKey("fieldsDegraded"),
                "「没有对象」不是失败，禁止顺带打上降级标记");
        assertFalse(result.containsKey("fieldsObject"));
    }

    @Test
    @DisplayName("MINIO／FILESYSTEM 占位面：显式 note，未实现面不折成不可用也不折成空源")
    void placeholderTypesReturnAnnotatedEmptySurfaceNot503() {
        Map<String, Object> minio = enterpriseService()
                .previewSchemaByConfig("MINIO", "{\"bucket\":\"raw\"}");
        assertEquals("MINIO", minio.get("type"));
        assertEquals(List.of(), minio.get("tableNames"));
        assertEquals(List.of(), minio.get("fields"));
        assertTrue(String.valueOf(minio.get("note")).contains("待实现"),
                "note 明写未实现，否则客户端读到空数组无从分辨「没对象」与「枚举没做」");

        Map<String, Object> fs = service.previewSchemaByConfig("FILESYSTEM", "{\"rootPath\":\"/data\"}");
        assertEquals(List.of(), fs.get("tableNames"));
        assertTrue(fs.containsKey("note"));
    }

    @Test
    @DisplayName("类型不在白名单：抛 IllegalArgumentException（端点落 400，属入参问题）")
    void unsupportedTypeLegThrowsBadRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> service.previewSchemaByConfig("SYBASE", "{\"jdbcUrl\":\"x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> service.previewSchemaByConfig("  ", "{\"jdbcUrl\":\"x\"}"));
    }
}
