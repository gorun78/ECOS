package com.chinacreator.gzcm.runtime.core.core.bean;

import java.io.Serializable;

public class Tddxnode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String node_id;
    private String node_name;
    private String inner_ip;
    private String outer_ip;
    private String node_port;

    public String getNode_id() {
        return node_id;
    }

    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }

    public String getNode_name() {
        return node_name;
    }

    public void setNode_name(String node_name) {
        this.node_name = node_name;
    }

    public String getInner_ip() {
        return inner_ip;
    }

    public void setInner_ip(String inner_ip) {
        this.inner_ip = inner_ip;
    }

    public String getOuter_ip() {
        return outer_ip;
    }

    public void setOuter_ip(String outer_ip) {
        this.outer_ip = outer_ip;
    }

    public String getNode_port() {
        return node_port;
    }

    public void setNode_port(String node_port) {
        this.node_port = node_port;
    }
    
    private String center_falg;
    
    public String getCenter_falg() {
        return center_falg;
    }
    
    public void setCenter_falg(String center_falg) {
        this.center_falg = center_falg;
    }
    
    private String is_cluster_logic_node;
    
    public String getIs_cluster_logic_node() {
        return is_cluster_logic_node;
    }
    
    public void setIs_cluster_logic_node(String is_cluster_logic_node) {
        this.is_cluster_logic_node = is_cluster_logic_node;
    }
    
    private String is_data_center;
    
    public String getIs_data_center() {
        return is_data_center;
    }
    
    public void setIs_data_center(String is_data_center) {
        this.is_data_center = is_data_center;
    }
}
