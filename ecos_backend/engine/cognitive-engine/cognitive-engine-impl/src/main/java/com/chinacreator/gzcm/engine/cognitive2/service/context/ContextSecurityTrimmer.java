package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;

import java.util.List;

/**
 * Context 权限/密级裁剪检查点（§5.4 步骤 4；security-engine 是权限的权威裁决方，
 * 本接口<b>只做接线点</b>，不复刻策略 —— 铁律 §2.4）。
 *
 * <p>三态判定：</p>
 * <ul>
 *   <li>{@code permitted=true, allVisible=true}：放行，内容全可见；</li>
 *   <li>{@code permitted=true, allVisible=false}：放行但源侧存在不可见行/列（Permissions 裁剪后
 *       部分遮蔽）⇒ Builder 生成 PERMISSION_GAP（MEDIUM）+ warning；</li>
 *   <li>{@code permitted=false}：该源整体拒绝；<b>必需源 ⇒ 403 E403_SOURCE_DENIED，
 *       可选源 ⇒ 移出 Pack + warning（禁止静默丢失）</b>。</li>
 * </ul>
 *
 * <p><b>fail-closed 红线</b>：检查点不可用（无注册实现 / 裁决异常）时，
 * Builder 按"security 依赖失败默认 DENY"处理（§5.5 / SUP-CTX-02：0 泄露），
 * 不降级放行。late-binding：宿主（gateway/aiming）装配 security REST 适配实现即可接线。</p>
 */
public interface ContextSecurityTrimmer {

    /** 裁剪判定结果。 */
    final class TrimDecision {
        private final boolean permitted;
        private final boolean allVisible;
        /** 不可见项定位（字段/行范围描述；禁带敏感正文，只带标识）。 */
        private final List<String> hiddenItems;

        public TrimDecision(boolean permitted, boolean allVisible, List<String> hiddenItems) {
            this.permitted = permitted;
            this.allVisible = allVisible;
            this.hiddenItems = hiddenItems == null ? List.of() : hiddenItems;
        }

        public static TrimDecision allowAll() {
            return new TrimDecision(true, true, List.of());
        }

        public static TrimDecision deny() {
            return new TrimDecision(false, false, List.of());
        }

        public static TrimDecision allowWithHidden(List<String> hiddenItems) {
            return new TrimDecision(true, false, hiddenItems);
        }

        public boolean isPermitted() { return permitted; }
        public boolean isAllVisible() { return allVisible; }
        public List<String> getHiddenItems() { return hiddenItems; }
    }

    /**
     * 对装配中的 Pack 执行权限/密级裁剪（行/列过滤、脱敏已在源引擎读接口执行，
     * 此处为主体验级授权复核 —— 双层防线，任一 DENY 即拒绝）。
     *
     * @param request    原始请求（含 scope.tenantId 主体范围）
     * @param ref        权威源条目
     * @param pack       组装中的 Pack（当前 sources 已含该源条目，实现可就地剔除/脱敏）
     */
    TrimDecision trim(ContextBuildRequest request, ContextSourceRef ref, ContextPack pack);
}
