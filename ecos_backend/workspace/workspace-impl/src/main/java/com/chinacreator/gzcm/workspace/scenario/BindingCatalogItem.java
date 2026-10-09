package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/** binding-catalog 单项（卷07 F07-05 N2 契约）。 */
public final class BindingCatalogItem {

    private final String type;
    private final String tier;
    private final String labelKey;
    private final String iconKey;
    private final List<String> linkableTo;

    public BindingCatalogItem(String type, String tier, String labelKey, String iconKey, List<String> linkableTo) {
        this.type = type;
        this.tier = tier;
        this.labelKey = labelKey;
        this.iconKey = iconKey;
        this.linkableTo = linkableTo;
    }

    public String getType() {
        return type;
    }

    public String getTier() {
        return tier;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getIconKey() {
        return iconKey;
    }

    public List<String> getLinkableTo() {
        return linkableTo;
    }
}
