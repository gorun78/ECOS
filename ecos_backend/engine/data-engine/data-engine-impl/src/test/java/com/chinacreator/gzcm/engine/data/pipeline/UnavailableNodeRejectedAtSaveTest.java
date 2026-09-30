package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.chinacreator.gzcm.common.exception.ValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-04 / W57 验收《UnavailableNodeRejectedAtSaveTest》— available=false 节点在<b>保存时即拒</b>。
 *
 * <p>设计 §F02-04：{@code available=false} 的节点类型（当前 {@code SOURCE_CDC}）
 * <b>在创建/更新 definition 时即拒</b>（抛 {@code ValidationException}，消息含
 * {@code ECOS-DATA-012}），而非执行时才拒（保留执行器显式拒绝为第二道防线）。</p>
 *
 * <p>本测试以 Mockito 隔离 {@link PipelineRepository} / {@link Pipeline SecurityService}，
 * 构造一个含 {@code SOURCE_CDC} 节点的 {@link PipelineSaveDTO}：</p>
 * <ul>
 *   <li>{@code createDefinition} 必须在 <b>saveNodesAndDependencies 落库前</b>抛
 *       {@code ECOS-DATA-012}（validateSaveDto 优先）；</li>
 *   <li>断言 {@code repository.insertDefinition} <b>从未</b>被调用（未落库）；</li>
 *   <li>对照：{@code SOURCE_JDBC}（available=true）节点不拒（同一 save 路径，validate 不放行）。</li>
 * </ul>
 */
@DisplayName("F02-04/W57：available=false 节点保存时即拒（ECOS-DATA-012）")
class UnavailableNodeRejectedAtSaveTest {

    @Test
    @DisplayName("含 SOURCE_CDC 节点的 createDefinition 抛 ECOS-DATA-012 且未落库")
    void sourceCdcRejectedAtSave() {
        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineSecurityService security = mock(PipelineSecurityService.class);
        PipelineServiceImpl service = new PipelineServiceImpl(repository, security);

        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("cdc-pipeline");
        PipelineSaveDTO.NodeSpec node = new PipelineSaveDTO.NodeSpec();
        node.setNodeId("n1");
        node.setType("SOURCE_CDC"); // available=false
        dto.setNodes(List.of(node));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.createDefinition(dto),
                "available=false 的 SOURCE_CDC 必须在保存时即拒");
        assertTrue(String.valueOf(ex.getMessage()).contains("ECOS-DATA-012"),
                "拒绝原因必须携带 ECOS-DATA-012 错误码，实际=" + ex.getMessage());

        // 关键：拒在 validateSaveDto（saveNodesAndDependencies 之前），未发生任何落库
        verify(repository, never()).insertDefinition(any(PipelineDefinition.class));
        verify(repository, never()).insertNode(any(PipelineNode.class));
    }

    @Test
    @DisplayName("对照组：SOURCE_JDBC（available=true）不触发 ECOS-DATA-012 节点拒绝")
    void availableNodeNotRejectedForAvailability() {
        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineSecurityService security = mock(PipelineSecurityService.class);
        PipelineServiceImpl service = new PipelineServiceImpl(repository, security);

        // insertDefinition 回传带 id 的实体，saveNodesAndDependencies 可继续
        when(repository.insertDefinition(any(PipelineDefinition.class)))
                .thenAnswer(inv -> {
                    PipelineDefinition def = inv.getArgument(0);
                    def.setId("def-0001");
                    return def;
                });

        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName("jdbc-pipeline");
        PipelineSaveDTO.NodeSpec node = new PipelineSaveDTO.NodeSpec();
        node.setNodeId("n1");
        node.setType("SOURCE_JDBC"); // available=true
        dto.setNodes(List.of(node));

        service.createDefinition(dto);

        // available=true → 不因可用性被拒；insertDefinition 被调用即证明越过可用性校验
        verify(repository).insertDefinition(any(PipelineDefinition.class));
        // 异常信息（即便有下游异常也不应为 012 节点拒）
        // 若 createDefinition 因 insertNode 真实落地抛 NPE/DB 异常属预期（mock 未 stub），
        // 故此处不断言 insertNode，仅断言「越过可用性拒绝、进入落库路径」。
        assertEquals(400, ValidationException.DEFAULT_HTTP_STATUS,
                "ValidationException 默认 400（ECOS-DATA-012 走 400）");
    }
}
