package com.chinacreator.gzcm.engine.data.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.dao.DataSourceDao;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 属主<b>服务侧</b>预览通道的携带态行为门（#142/B4-3e-44，收口差距 <b>D-B4-75</b>）。
 *
 * <p><b>为什么端点行为门不够</b>：同批新增的
 * {@code DataSourcePreviewStatusFidelityTest} 里 {@code DataSourceServiceImpl} 是 <b>mock</b>，
 * 它只能证明「端点把携带态渲染成真状态行」，证明不了「服务侧真的抛出携带态」。
 * 本门直接构造真服务（依赖 mock），钉住改前的三处失真：</p>
 * <ul>
 *   <li>记录不存在 → 改前抛<b>无码</b> {@code RuntimeException}，端点只能猜（并折成 200＋code 404）；
 *       改后抛 {@link DataBridgeException} 且 {@code httpStatus}＝{@code errorCode}＝404、
 *       message 前缀带 {@code ECOS-DATA-032}（§3.2.7 同号性）；</li>
 *   <li>底座取不到 → 改前 {@code result.put("error", …)} 后<b>正常返回</b>，
 *       客户端读到「0 个对象」与「该源确实没有对象」同形（§4.7.2 违例）；
 *       改后抛携带 503＋{@code ECOS-DATA-051}，cause 保留原始原因；</li>
 *   <li>对象存储／文件源 → 有意<b>不</b>一起作废：仍返回成功占位载荷
 *       （防止把「未实现的枚举面」过度折叠成不可用＝反向失真）。</li>
 * </ul>
 *
 * <p><b>已知边界</b>：本门不覆盖内层「列预览部分失败」腿（需要真 Connector 夹具，
 * 属 runtime-access 侧集成面）。该腿的显式降级键由静态门
 * {@code SuccessStatusFailureEnvelopeRatchetTest} 判据 5 与源码锚点承担，未宣称已实跑覆盖。</p>
 */
class DataSourcePreviewCarriedThrowTest {

    private final DataSourceDao dao = mock(DataSourceDao.class);
    private final ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
    private final DataSourceServiceImpl service = new DataSourceServiceImpl(dao, connectorFactory);

    @Test
    @DisplayName("记录不存在腿：抛携带 404 的 DataBridgeException，不再抛无码 RuntimeException")
    void notFoundLegThrowsCarriedDataBridgeException() {
        when(dao.findByDatasourceId("missing")).thenReturn(List.of());

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> service.previewSchema("missing"));

        assertEquals(HttpStatus.NOT_FOUND.value(), thrown.getHttpStatus(),
                "不存在腿必须携带 404（改前无码，端点只能折成 200）");
        assertEquals(HttpStatus.NOT_FOUND.value(), thrown.getErrorCode(),
                "§3.2.7 同号性：状态行与信封码同源，不得两个数各写一遍");
        assertTrue(thrown.getMessage().startsWith(DataDomainErrorCode.ECOS_DATA_032),
                "机读码取本域码表 404 段前缀，实际: " + thrown.getMessage());
    }

    @Test
    @DisplayName("底座不可用腿：抛携带 503＋ECOS-DATA-051，不再用成功体里的 error 键表达不可用")
    void unavailableChannelLegThrowsCarried503InsteadOfErrorKey() {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceType("MYSQL");
        ds.setConnectionConfig("{\"host\":\"127.0.0.1\",\"port\":3306}");
        when(dao.findByDatasourceId("ds-1")).thenReturn(List.of(ds));
        when(connectorFactory.getConnector(anyString()))
                .thenThrow(new IllegalStateException("runtime-access connector absent"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> service.previewSchema("ds-1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), thrown.getHttpStatus(),
                "不可用必须与「查到了但没结构」分态（§4.7.2）");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), thrown.getErrorCode());
        assertTrue(thrown.getMessage().startsWith(DataDomainErrorCode.ECOS_DATA_051),
                "机读码取 runtime-access JDBC 管道段，实际: " + thrown.getMessage());
        assertNotNull(thrown.getCause(), "原始原因必须随 cause 保留，不得只留一句折叠后的话术");
    }

    @Test
    @DisplayName("对象存储／文件源：仍返回成功占位载荷，禁止把未实现面一并折成不可用")
    void objectStorageLegStillReturnsSuccessPlaceholder() {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceType("MINIO");
        ds.setConnectionConfig("{\"bucket\":\"raw\"}");
        when(dao.findByDatasourceId("ds-minio")).thenReturn(List.of(ds));

        Map<String, Object> result = service.previewSchema("ds-minio");

        assertEquals("MINIO", result.get("type"), "成功腿的类型回显不得丢");
        assertEquals(List.of(), result.get("objects"),
                "占位面＝空集合且无 error 键；与「不可用」分态靠异常，不靠载荷里塞 error");
        assertTrue(!result.containsKey("error"),
                "成功载荷里不得再出现 error 键（D-B4-75 折叠形态）");
    }
}
