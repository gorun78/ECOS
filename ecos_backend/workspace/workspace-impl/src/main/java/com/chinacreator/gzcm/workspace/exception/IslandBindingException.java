package com.chinacreator.gzcm.workspace.exception;

import java.util.List;

/**
 * 激活场景时存在孤岛绑定（F07-04 / C-5）。HTTP 409，错误码 ISLAND_BINDING，携带孤岛清单。
 */
public class IslandBindingException extends WorkspaceException {

    public static final String CODE = "ISLAND_BINDING";

    /** 单个孤岛条目：bindingId / name / bindingType / reason */
    public record IslandItem(String bindingId, String name, String bindingType, String reason) {
    }

    private final List<IslandItem> islands;

    public IslandBindingException(String message, List<IslandItem> islands) {
        super(409, 409, CODE, message);
        this.islands = islands == null ? List.of() : List.copyOf(islands);
    }

    public List<IslandItem> getIslands() {
        return islands;
    }
}
