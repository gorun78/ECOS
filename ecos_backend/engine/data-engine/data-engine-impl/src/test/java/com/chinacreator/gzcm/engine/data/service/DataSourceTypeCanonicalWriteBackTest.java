package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.dao.DataSourceDao;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据源类型「写回规范名」行为门（#142/B4-3e-63，收口差距 <b>D-B4-78</b> 的属主写腿）。
 *
 * <p><b>本门为什么必须存在</b>：词表收敛到 {@code DataSourceTypeVocabulary} 单源后，
 * 服务准入面（{@code SUPPORTED_TYPES}）与连接器归一面都成了派生值，但<b>落库值</b>这一面
 * 不在任何派生关系里——它由 {@code DataSourceServiceImpl} 的 register／updateDataSource
 * 两条写腿自己决定。收口前的写法是「大写入参原样落库」，于是同一枚数据源在前端叫
 * {@code gaussdb}、在后端承流叫 {@code GAUSS}、在库里却可能叫 {@code GAUSSDB}（若哪天放开别名准入），
 * 三面各存一份拼写正是 D-B4-78 的病灶本身。承流侧改名而不钉写回面，等于把分裂从
 * 「准入拒」挪成「库里多一枚同物异名」，且这类漂移没有任何编译期或门禁信号。
 * 本门以 mock DAO + {@link ArgumentCaptor} 直接观察<b>送进 SQL 的那一枚字符串</b>。</p>
 *
 * <p>钉值口径：期望值全部是<b>字面量</b>（{@code "GAUSS"}／{@code "MSSQL"}／{@code "POSTGRESQL"}），
 * 不由 {@code DataSourceTypeVocabulary} 推导——否则判据和被检面同源，词表改错时本门跟着一起绿。
 * 另配 {@link #aliasIsNotPersistedVerbatim} 一条反证，把「规范写回」与「原样大写」两种实现区分开，
 * 证明本门对「去掉 canonical 一步」的变异能变红（而不是恒等于输入的大写形态）。</p>
 *
 * <p>覆盖面：register 与 updateDataSource 两条写腿同一口径；词表外名与被登记面外名都显式拒绝且
 * <b>完全不触库</b>；MINIO 的版本档位门不因词表归一而放宽。真连库／真 profile 面不在本门范围
 * （由 {@code ConnectionTestViaRuntimeTest} 与部署侧档位配置承担）。</p>
 */
class DataSourceTypeCanonicalWriteBackTest {

    private DataSourceDao dao;

    /** 建服务并记住其 DAO mock（夹具口径与 {@code DataSourceServiceImplDsPlainTest} 一致）。 */
    private DataSourceServiceImpl newService() {
        dao = mock(DataSourceDao.class);
        return new DataSourceServiceImpl(dao, mock(ConnectorFactory.class));
    }

    private static DataSourceDTO dto(String type) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setDatasourceName("src-" + type);
        dto.setDatasourceType(type);
        dto.setOrgId("org-1");
        dto.setDescription("gate");
        // 不带口令：避开 PMO-49 加密出口腿，本门只看类型写回
        dto.setConnectionConfig("{\"host\":\"10.0.0.1\",\"port\":\"5432\"}");
        return dto;
    }

    /** 取实际送进 DAO 的类型形参——这是库里会长出的那一枚值。 */
    private String capturedRegisterType() {
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(dao).register(anyString(), anyString(), type.capture(),
                any(), any(), any(), any(), any(), any(), any());
        return type.getValue();
    }

    private String capturedUpdateType() {
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(dao).updateDataSource(anyString(), anyString(), type.capture(),
                any(), any(), any(), any(), any(), any(), any());
        return type.getValue();
    }

    @Test
    @DisplayName("register 别名入参 gaussdb → 落库规范名 GAUSS（钉字面量，非派生）")
    void registerWritesCanonicalNotAlias() {
        newService().register(dto("gaussdb"));
        assertEquals("GAUSS", capturedRegisterType(),
                "别名形态必须写成词表规范名，否则 td_datasource 与承流面同物异名");
    }

    @Test
    @DisplayName("反证：写回的不是「入参原样大写」——GAUSSDB ≠ GAUSS")
    void aliasIsNotPersistedVerbatim() {
        newService().register(dto("gaussdb"));
        String persisted = capturedRegisterType();
        assertNotEquals("GAUSSDB", persisted,
                "若这里等于 GAUSSDB，说明 canonical 那一步被摘掉了，写腿退回原样大写");
    }

    @Test
    @DisplayName("register 别名 SQLSERVER → 落库规范名 MSSQL（工厂侧同物异名的另一枚）")
    void registerWritesCanonicalForSqlServerAlias() {
        newService().register(dto("SQLSERVER"));
        assertEquals("MSSQL", capturedRegisterType());
    }

    @Test
    @DisplayName("对照：规范名入参 POSTGRESQL 原样写回，证明本门不是恒把输入改掉")
    void canonicalInputStaysCanonical() {
        newService().register(dto("POSTGRESQL"));
        assertEquals("POSTGRESQL", capturedRegisterType());
    }

    @Test
    @DisplayName("register 词表外类型 s3 → 显式拒绝且完全不触库（归一不扩准入）")
    void unknownTypeNeverTouchesDao() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().register(dto("s3")));
        assertTrue(ex.getMessage().contains("不支持的数据源类型"),
                "拒绝文案须点名类型不支持: " + ex.getMessage());
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("register 工厂可归一但服务不可登记的 mongodb → 同样拒绝（两面不同集合被钉住）")
    void connectorOnlyTypeIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().register(dto("MONGODB")));
        assertTrue(ex.getMessage().contains("MONGODB"), "文案须带回被拒的入参名: " + ex.getMessage());
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("register MINIO 且档位不可用 → 版本门仍在位（词表归一没有放宽 MINIO 档位判据）")
    void minioProfileGateSurvivesVocabularyUnification() {
        // 纯单测无 Spring Environment ⇒ resolveProfile() 为 null ⇒ 保守判不可用
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().register(dto("MINIO")));
        assertTrue(ex.getMessage().contains("enterprise"),
                "MINIO 仍须由版本档位显式承载: " + ex.getMessage());
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("updateDataSource 别名入参 → 与 register 同一口径写回规范名")
    void updateWritesCanonicalToo() {
        DataSourceServiceImpl svc = newService();
        DataSourceEntity existing = new DataSourceEntity();
        existing.setDatasourceId("ds-1");
        existing.setDatasourceType("GAUSS");
        existing.setMetadataConfig("{}");
        when(dao.findByDatasourceId("ds-1")).thenReturn(List.of(existing));

        svc.updateDataSource("ds-1", dto("gaussdb"));

        assertEquals("GAUSS", capturedUpdateType(),
                "更新腿与登记腿必须同一写回口径，否则 PUT 一次就把库里值改回别名形态");
    }

    @Test
    @DisplayName("两腿同枚入参得到同枚落库值（register/update 口径不漂移）")
    void bothWriteLegsAgree() {
        DataSourceServiceImpl a = newService();
        a.register(dto("gaussdb"));
        String fromRegister = capturedRegisterType();

        DataSourceServiceImpl b = newService();
        DataSourceEntity existing = new DataSourceEntity();
        existing.setDatasourceId("ds-2");
        existing.setMetadataConfig("{}");
        when(dao.findByDatasourceId("ds-2")).thenReturn(List.of(existing));
        b.updateDataSource("ds-2", dto("gaussdb"));
        String fromUpdate = capturedUpdateType();

        assertEquals(fromRegister, fromUpdate, "同一枚类型经两条写腿必须落成同一枚规范名");
    }
}
