package com.chinacreator.gzcm.workspace.exception;

/**
 * 口径属主引擎显式失败（#146ac 附录A 第 31 条读面三枚，HTTP 500 / {@code ONTOLOGY_FAILURE}）。
 *
 * <p>与 {@link CaliberOwnerUnavailableException} 的分工：本态是<b>属主自己回了失败信封</b>
 * （状态行已定、只是不承载在 HTTP 上——属主 {@code CaliberController} 全类裸回
 * {@code ApiResponse}，失败只体现在信封数字码面，属 D-B4-59 裸回族在册欠账），
 * 而不可得态是<b>根本没拿到应答</b>。两者对运维的处置不同（查属主日志 vs 查链路/端口），
 * 禁合并成一枚码。</p>
 *
 * <p>只带属主的机读码（{@code ECOS-ONTO-0xx} 形态），不带其 message 原文（X-41）。</p>
 */
public class CaliberOwnerFailureException extends WorkspaceDomainException {

    public CaliberOwnerFailureException(String operation, String ownerErrorCode, int ownerCode) {
        super(500, "ONTOLOGY_FAILURE", "口径属主引擎报告内部失败，请凭追踪号联系管理员");
        putDetail("engine", "ontology");
        putDetail("operation", operation);
        putDetail("ownerErrorCode", ownerErrorCode);
        putDetail("ownerCode", ownerCode);
    }
}
