package com.chinacreator.gzcm.workspace.dto;

import java.util.List;

/**
 * 实体完整状态机定义：{@code entityCode/states/transitions/totalTransitions}。
 * {@code states} 为去重后的状态集合（按定义出现序）。
 */
public record StateMachineVO(
        String entityCode,
        List<String> states,
        List<StateTransitionVO> transitions,
        int totalTransitions) {
}
