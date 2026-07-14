package com.chinacreator.gzcm.runtime.core.common.nodemgr.bean;

import java.io.Serializable;

public class NodeProcessBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private String node_id;
    private String process_name;
    private String process_desc;
    private String process_state;

    public String getNode_id() {
        return node_id;
    }

    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }

    public String getProcess_name() {
        return process_name;
    }

    public void setProcess_name(String process_name) {
        this.process_name = process_name;
    }

    public String getProcess_desc() {
        return process_desc;
    }

    public void setProcess_desc(String process_desc) {
        this.process_desc = process_desc;
    }

    public String getProcess_state() {
        return process_state;
    }

    public void setProcess_state(String process_state) {
        this.process_state = process_state;
    }
}
