package com.chinacreator.gzcm.workspace.scenario;

/** SANDBOX 上下文写非白名单表（卷07 F07-08 C-7；正常路径不可达的内部防御，HTTP 500）。 */
public class SandboxWriteForbiddenException extends ScenarioDomainException {

    public SandboxWriteForbiddenException(String targetTable) {
        super(500, "SANDBOX_WRITE_FORBIDDEN", "演练上下文禁止写事实表: " + targetTable);
        putDetail("targetTable", targetTable);
    }
}
