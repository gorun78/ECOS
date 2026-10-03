package com.chinacreator.gzcm.engine.ontology.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁违反项（F03-03 / D.3 GateViolation）。refs 最多 20 条。
 */
public class GateViolation {

    public enum Gate { V1_CALIBER, V2_UNIT, V3_LINEAGE }

    private String gate;
    private String code;
    private String message;
    private List<String> refs = new ArrayList<>();

    public GateViolation() {}

    public GateViolation(Gate gate, String code, String message) {
        this.gate = gate.name();
        this.code = code;
        this.message = message;
    }

    public void addRef(String ref) {
        if (refs.size() < 20 && ref != null) refs.add(ref);
    }

    public String getGate() { return gate; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<String> getRefs() { return refs; }
}
