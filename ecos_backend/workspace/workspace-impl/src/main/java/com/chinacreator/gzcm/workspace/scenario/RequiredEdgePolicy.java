package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E_req 推导的单点（卷07 F07-03-1，R-27 两口径切换只改一个实现）。
 * <p>同一份声明同时导出邻接表（linkableTo，供 F07-05 binding-catalog），禁两处各写一份。</p>
 */
public interface RequiredEdgePolicy {

    /** 对给定已绑定类型集合推导必需边（固定分母实现恒返回三条；动态实现仅返回已配对者）。 */
    List<RequiredEdge> derive(Set<BindingType> boundTypes);

    /** 邻接可达：每个绑定类型 → 可连的目标类型集合（binding-catalog 的 linkableTo 由此导出）。 */
    Map<BindingType, Set<BindingType>> adjacency();
}
