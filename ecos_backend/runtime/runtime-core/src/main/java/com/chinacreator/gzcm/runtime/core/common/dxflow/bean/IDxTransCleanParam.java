package com.chinacreator.gzcm.runtime.core.common.dxflow.bean;

/**
 * IDxTransCleanParam interface
 * TODO: Add proper implementation based on actual requirements
 */
public interface IDxTransCleanParam {
    String getSchedule_id();
    void setSchedule_id(String schedule_id);
    String getOn_rule_type();
    void setOn_rule_type(String on_rule_type);
    String getCleanrule_id();
    void setCleanrule_id(String cleanrule_id);
    String getId();
    void setId(String id);
    String getRule_name();
    void setRule_name(String rule_name);
    String getRule_type();
    void setRule_type(String rule_type);
    String getInput_column_code();
    void setInput_column_code(String input_column_code);
    Integer getSort_sn();
    void setSort_sn(Integer sort_sn);
    String getCleanrule_name();
    void setCleanrule_name(String cleanrule_name);
    String getQuote_level();
    void setQuote_level(String quote_level);
    String getCreate_time();
    void setCreate_time(String create_time);
    String getCleanrule_descr();
    void setCleanrule_descr(String cleanrule_descr);
    String getOutput_column_code();
    void setOutput_column_code(String output_column_code);
}

