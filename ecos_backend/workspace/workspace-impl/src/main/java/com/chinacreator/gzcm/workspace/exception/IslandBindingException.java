package com.chinacreator.gzcm.workspace.exception;

import java.util.List;
import java.util.Map;

/** 孤岛绑定阻断激活（F07-04，HTTP 409 / ISLAND_BINDING）。 */
public class IslandBindingException extends WorkspaceDomainException {

    public IslandBindingException(List<Map<String, Object>> islands) {
        super(409, "ISLAND_BINDING", "存在孤岛绑定，禁止激活");
        putDetail("islands", islands);
    }
}
