package com.chinacreator.gzcm.sysman.audit.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * P1-2: 密码学审计服务。
 * SHA-256 链式哈希存储，支持链完整性验证。
 */
@Service
public class CryptoAuditService {
    private static final Logger log = LoggerFactory.getLogger(CryptoAuditService.class);

    private final ConcurrentHashMap<String, CryptoAuditLedger> store = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String computeHash(CryptoAuditLedger entry) {
        String data = (entry.getPrevHash() != null ? entry.getPrevHash() : "")
                + (entry.getEventType() != null ? entry.getEventType() : "")
                + (entry.getResource() != null ? entry.getResource() : "")
                + (entry.getAction() != null ? entry.getAction() : "")
                + (entry.getOperatorId() != null ? entry.getOperatorId() : "")
                + (entry.getPayload() != null ? entry.getPayload() : "")
                + entry.getTimestamp();
        return sha256(data);
    }

    public synchronized CryptoAuditLedger record(CryptoAuditLedger entry) {
        entry.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entry.setTimestamp(System.currentTimeMillis());

        String prevId = order.peekLast();
        if (prevId != null) {
            CryptoAuditLedger prev = store.get(prevId);
            if (prev != null) entry.setPrevHash(prev.getCurrentHash());
        }
        entry.setCurrentHash(computeHash(entry));
        entry.setVerified(true);

        store.put(entry.getId(), entry);
        order.addLast(entry.getId());
        log.info("加密审计记录: id={}, type={}, action={}", entry.getId(), entry.getEventType(), entry.getAction());
        return entry;
    }

    public Map<String, Object> chainVerify() {
        List<Map<String, Object>> tampered = new ArrayList<>();
        int total = 0;
        int pass = 0;

        List<CryptoAuditLedger> chain = new ArrayList<>();
        for (String id : order) {
            CryptoAuditLedger e = store.get(id);
            if (e != null) chain.add(e);
        }

        String expectedPrev = null;
        for (int i = 0; i < chain.size(); i++) {
            CryptoAuditLedger e = chain.get(i);
            total++;
            boolean ok = true;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", e.getId());

            if (i == 0) {
                if (e.getPrevHash() != null) {
                    ok = false;
                    detail.put("issue", "链首 prevHash 应为 null，实际: " + e.getPrevHash());
                }
            } else {
                if (!Objects.equals(expectedPrev, e.getPrevHash())) {
                    ok = false;
                    detail.put("issue", "prevHash 断裂: 期望=" + expectedPrev + " 实际=" + e.getPrevHash());
                }
            }

            String recomputed = computeHash(e);
            if (!Objects.equals(recomputed, e.getCurrentHash())) {
                ok = false;
                detail.put("issue", (detail.containsKey("issue") ? detail.get("issue") + "; " : "")
                        + "currentHash 不匹配: 期望=" + recomputed + " 实际=" + e.getCurrentHash());
            }

            if (ok) {
                e.setVerified(true);
                pass++;
            } else {
                e.setVerified(false);
                tampered.add(detail);
            }
            expectedPrev = e.getCurrentHash();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pass", pass);
        result.put("fail", tampered.size());
        result.put("intact", tampered.isEmpty());
        result.put("tampered", tampered);
        return result;
    }

    public List<CryptoAuditLedger> list(String keyword, int page, int pageSize) {
        List<CryptoAuditLedger> all = new ArrayList<>();
        List<String> ids = new ArrayList<>(order);
        Collections.reverse(ids);
        for (String id : ids) {
            CryptoAuditLedger e = store.get(id);
            if (e != null) {
                if (keyword != null && !keyword.isEmpty()) {
                    String kw = keyword.toLowerCase();
                    if ((e.getEventType() != null && e.getEventType().toLowerCase().contains(kw))
                            || (e.getAction() != null && e.getAction().toLowerCase().contains(kw))
                            || (e.getOperatorId() != null && e.getOperatorId().toLowerCase().contains(kw))) {
                        all.add(e);
                    }
                } else {
                    all.add(e);
                }
            }
        }
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        return from < all.size() ? all.subList(from, to) : Collections.emptyList();
    }

    public int count(String keyword) {
        int n = 0;
        for (String id : order) {
            CryptoAuditLedger e = store.get(id);
            if (e != null) {
                if (keyword != null && !keyword.isEmpty()) {
                    String kw = keyword.toLowerCase();
                    if ((e.getEventType() != null && e.getEventType().toLowerCase().contains(kw))
                            || (e.getAction() != null && e.getAction().toLowerCase().contains(kw))
                            || (e.getOperatorId() != null && e.getOperatorId().toLowerCase().contains(kw))) {
                        n++;
                    }
                } else {
                    n++;
                }
            }
        }
        return n;
    }

    public CryptoAuditLedger getById(String id) {
        return store.get(id);
    }
}
