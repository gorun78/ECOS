package com.chinacreator.gzcm.sysman.audit.crypto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

/**
 * P1-2: 密码学审计账本实体。
 * SHA-256 链式哈希，不可篡改。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CryptoAuditLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String eventType;
    private String resource;
    private String action;
    private String operatorId;
    private String payload;
    private String prevHash;
    private String currentHash;
    private Long timestamp;
    private Boolean verified = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }

    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
}
