package com.chinacreator.gzcm.engine.ai.oag;

/**
 * OAG 管道节点接口 — 8步 DAG 的每一步实现此接口。
 */
@FunctionalInterface
public interface OagNode {

    /**
     * 执行本节点的处理逻辑。
     *
     * @param ctx 管道上下文（读写）
     * @return 上下文引用（链式调用）
     */
    OagPipelineContext execute(OagPipelineContext ctx);

    /** 节点名称 */
    default String name() {
        return getClass().getSimpleName();
    }
}
