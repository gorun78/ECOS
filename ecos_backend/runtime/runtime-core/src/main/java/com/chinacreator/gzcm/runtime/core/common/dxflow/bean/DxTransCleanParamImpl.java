package com.chinacreator.gzcm.runtime.core.common.dxflow.bean;

/**
 * Simple implementation of IDxTransCleanParam for query criteria
 */
public class DxTransCleanParamImpl implements IDxTransCleanParam {
    private static final long serialVersionUID = 1L;
    
    private String schedule_id;
    private String on_rule_type;
    private String cleanrule_id;
    private String id;
    private String rule_name;
    private String rule_type;
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public String getOn_rule_type() {
        return on_rule_type;
    }
    
    public void setOn_rule_type(String on_rule_type) {
        this.on_rule_type = on_rule_type;
    }
    
    public String getCleanrule_id() {
        return cleanrule_id;
    }
    
    public void setCleanrule_id(String cleanrule_id) {
        this.cleanrule_id = cleanrule_id;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getRule_name() {
        return rule_name;
    }
    
    public void setRule_name(String rule_name) {
        this.rule_name = rule_name;
    }
    
    public String getRule_type() {
        return rule_type;
    }
    
    public void setRule_type(String rule_type) {
        this.rule_type = rule_type;
    }
    
    private String input_column_code;
    
    public String getInput_column_code() {
        return input_column_code;
    }
    
    public void setInput_column_code(String input_column_code) {
        this.input_column_code = input_column_code;
    }
    
    private Integer sort_sn;
    private String cleanrule_name;
    private String quote_level;
    private String create_time;
    private String cleanrule_descr;
    
    public Integer getSort_sn() {
        return sort_sn;
    }
    
    public void setSort_sn(Integer sort_sn) {
        this.sort_sn = sort_sn;
    }
    
    public String getCleanrule_name() {
        return cleanrule_name;
    }
    
    public void setCleanrule_name(String cleanrule_name) {
        this.cleanrule_name = cleanrule_name;
    }
    
    public String getQuote_level() {
        return quote_level;
    }
    
    public void setQuote_level(String quote_level) {
        this.quote_level = quote_level;
    }
    
    public String getCreate_time() {
        return create_time;
    }
    
    public void setCreate_time(String create_time) {
        this.create_time = create_time;
    }
    
    public String getCleanrule_descr() {
        return cleanrule_descr;
    }
    
    public void setCleanrule_descr(String cleanrule_descr) {
        this.cleanrule_descr = cleanrule_descr;
    }
    
    private String output_column_code;
    
    public String getOutput_column_code() {
        return output_column_code;
    }
    
    public void setOutput_column_code(String output_column_code) {
        this.output_column_code = output_column_code;
    }
}
