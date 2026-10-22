package com.chinacreator.gzcm.common.data.model;

/**
 * 列元数据面 — runtime-access 底座向引擎层回传「某个数据对象有哪些列」的统一载体。
 *
 * <p><b>为什么需要一个专门的载体（#142/B4-3e-46，收口差距 D-B4-76）</b>：
 * 底座此前的 {@code Connector} 契约只有「资源清单」与「样本行」两面——样本行是
 * {@code Map<String,Object>}，只能给出<b>列名与一个取值</b>，给不出类型／可空／备注。
 * 新建数据源表单的列面预览因此没有可信来源：要么读空（缺陷现形），要么由上层
 * 从 Java 值类型反推 JDBC 类型（＝伪造，正是本仓反复收口的「把猜的写成真的」）。</p>
 *
 * <p><b>本载体的空值口径（铁律级，消费侧不得改写）</b>：{@code typeName}／{@code required}
 * ／{@code comment} 为 {@code null} 只允许有一种含义＝<b>该连接器拿不到这一属性</b>。
 * 上层渲染时必须把它显示成「未知／—」，禁止替换成 {@code "VARCHAR"}、{@code false}
 * 或空串——那等于把「底座没说」折叠成「底座说是」。</p>
 *
 * <p>{@code name} 是唯一必填面：列名拿不到就不构成为一个列。</p>
 *
 * @author ECOS-BE (#142/B4-3e-46)
 */
public class ColumnMetadata {

    /** 列名（必填，底座未提供列名时不得构造本对象）。 */
    private final String name;

    /** 声明类型名（如 {@code varchar}／{@code int4}）；连接器不提供时为 {@code null}。 */
    private final String typeName;

    /** 是否必填（NOT NULL）；连接器不提供可空性时为 {@code null}。 */
    private final Boolean required;

    /** 列备注；连接器不提供注释面时为 {@code null}。 */
    private final String comment;

    public ColumnMetadata(String name, String typeName, Boolean required, String comment) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("列名不得为空：无列名的行不构成列面，禁止以占位名冒充");
        }
        this.name = name;
        this.typeName = typeName;
        this.required = required;
        this.comment = comment;
    }

    /** 只有列名的最简形态（Csv／无声明列面场景），其余属性一律 {@code null}＝未知。 */
    public static ColumnMetadata nameOnly(String name) {
        return new ColumnMetadata(name, null, null, null);
    }

    public String getName() {
        return name;
    }

    public String getTypeName() {
        return typeName;
    }

    public Boolean getRequired() {
        return required;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public String toString() {
        return "ColumnMetadata{name=" + name + ", typeName=" + typeName
                + ", required=" + required + ", comment=" + comment + '}';
    }
}
