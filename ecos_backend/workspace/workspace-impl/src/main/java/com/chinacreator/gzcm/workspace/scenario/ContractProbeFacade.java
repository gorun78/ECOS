package com.chinacreator.gzcm.workspace.scenario;

/**
 * 契约探测的门面出口（卷07 F07-03-2，探测只走 api 门面 HTTP，禁直读对方 schema）。
 * <p>各引擎（data/ontology/kb/ai）的探测端点由属主分册暴露；本接口是 workspace 侧的唯一抽象，
 * 便于装配两态实现（gateway 本地 bean / 独立进程 REST）。实现失败应抛异常，由 {@link EdgeContractProbe}
 * 降级为 missing，绝不向上传播让端点 500。</p>
 */
@FunctionalInterface
public interface ContractProbeFacade {

    /** 取回某必需边两端的契约计数与最近校验时间。 */
    ContractSnapshot fetch(RequiredEdge edge);
}
