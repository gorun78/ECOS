package com.chinacreator.gzcm.runtime.core.security.policy.pdp;

import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.chinacreator.gzcm.runtime.core.security.policy.cache.DecisionCacheService;
import com.chinacreator.gzcm.sysman.policy.model.AccessRequest;
import com.chinacreator.gzcm.sysman.policy.model.PolicyContext;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;
import com.chinacreator.gzcm.sysman.policy.pap.PolicyAdministrationPoint;
import com.chinacreator.gzcm.sysman.policy.pip.PolicyInformationPoint;
import com.chinacreator.gzcm.sysman.policy.pdp.PolicyDecisionPoint;

/**
 * 策略决策点实现：整合RBAC、ABAC和数据权限进行统一决策
 * 支持决策结果缓存和并行评估以提升性能
 */
public class PolicyDecisionPointImpl implements PolicyDecisionPoint {

    private static final Logger logger = LoggerFactory.getLogger(PolicyDecisionPointImpl.class);

    private final IPermissionCheckService rbacChecker;
    private final IAbacPermissionChecker abacChecker;
    private final PolicyInformationPoint pip;
    private final PolicyAdministrationPoint pap;
    private final DecisionCacheService decisionCache;

    public PolicyDecisionPointImpl(IPermissionCheckService rbacChecker,
                                   IAbacPermissionChecker abacChecker,
                                   PolicyInformationPoint pip,
                                   PolicyAdministrationPoint pap) {
        this(rbacChecker, abacChecker, pip, pap, null);
    }

    public PolicyDecisionPointImpl(IPermissionCheckService rbacChecker,
                                   IAbacPermissionChecker abacChecker,
                                   PolicyInformationPoint pip,
                                   PolicyAdministrationPoint pap,
                                   DecisionCacheService decisionCache) {
        this.rbacChecker = rbacChecker;
        this.abacChecker = abacChecker;
        this.pip = pip;
        this.pap = pap;
        this.decisionCache = decisionCache;
    }

    @Override
    public PolicyDecision evaluate(AccessRequest request) throws PolicyEvaluationException {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 检查决策结果缓存
            String cacheKey = generateCacheKey(request);
            if (decisionCache != null) {
                PolicyDecision cachedDecision = decisionCache.get(cacheKey);
                if (cachedDecision != null) {
                    logger.debug("决策结果缓存命中: cacheKey={}", cacheKey);
                    return cachedDecision;
                }
            }

            // 2. 构建策略上下文
            PolicyContext context = buildContext(request);

            // 3. 先进行RBAC检查（快速路径）
            if (request.getUserId() != null && request.getResource() != null && request.getAction() != null) {
                try {
                    boolean rbacAllowed = rbacChecker.checkPermission(
                            request.getUserId(),
                            request.getResource(),
                            request.getAction()
                    );
                    if (!rbacAllowed) {
                        PolicyDecision decision = new PolicyDecision(PolicyDecision.Decision.DENY);
                        decision.setReason("RBAC权限检查失败");
                        decision.setTimestamp(System.currentTimeMillis());
                        
                        // 缓存决策结果
                        if (decisionCache != null) {
                            decisionCache.put(cacheKey, decision, 0); // 使用默认TTL
                        }
                        
                        return decision;
                    }
                } catch (Exception e) {
                    logger.warn("RBAC检查失败，继续ABAC检查: {}", e.getMessage());
                    // RBAC检查失败，继续ABAC检查
                }
            }

            // 4. 进行ABAC检查
            AbacContext abacContext = convertToAbacContext(context);
            IAbacPermissionChecker.Decision abacDecision = abacChecker.check(abacContext);

            // 5. 转换为统一决策结果
            PolicyDecision decision = convertToPolicyDecision(abacDecision);
            decision.setReason("策略评估完成");
            decision.setTimestamp(System.currentTimeMillis());

            // 6. 添加义务（如需要脱敏、审计等）
            addObligations(decision, request, context);

            // 7. 缓存决策结果
            if (decisionCache != null) {
                decisionCache.put(cacheKey, decision, 0); // 使用默认TTL
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.debug("策略评估完成: cacheKey={}, decision={}, duration={}ms", cacheKey, decision.getDecision(), duration);

            return decision;
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            throw new PolicyEvaluationException("策略评估失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEvaluationException("策略评估异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(AccessRequest request) {
        // 计算上下文哈希（用于区分不同的上下文）
        String contextHash = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            if (request.getSubjectAttributes() != null) {
                sb.append(request.getSubjectAttributes().toString());
            }
            if (request.getResourceAttributes() != null) {
                sb.append(request.getResourceAttributes().toString());
            }
            if (request.getEnvironmentAttributes() != null) {
                sb.append(request.getEnvironmentAttributes().toString());
            }
            byte[] hash = md.digest(sb.toString().getBytes());
            contextHash = bytesToHex(hash).substring(0, 8); // 取前8位
        } catch (Exception e) {
            logger.warn("生成上下文哈希失败", e);
        }
        
        if (decisionCache != null) {
            return decisionCache.generateCacheKey(
                request.getUserId(),
                request.getResource(),
                request.getAction(),
                contextHash
            );
        }
        
        // 如果没有缓存服务，返回简单键
        return String.format("%s:%s:%s", 
            request.getUserId(), 
            request.getResource(), 
            request.getAction()
        );
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private PolicyContext buildContext(AccessRequest request) {
        PolicyContext context = new PolicyContext();

        // 从PIP获取属性
        Map<String, Object> subjectAttrs = request.getSubjectAttributes();
        if (subjectAttrs == null || subjectAttrs.isEmpty()) {
            subjectAttrs = pip.getUserAttributes(request.getUserId());
        }
        context.setSubject(subjectAttrs);

        Map<String, Object> resourceAttrs = request.getResourceAttributes();
        if (resourceAttrs == null || resourceAttrs.isEmpty()) {
            resourceAttrs = pip.getResourceAttributes(request.getResource());
        }
        context.setResource(resourceAttrs);

        Map<String, Object> envAttrs = request.getEnvironmentAttributes();
        if (envAttrs == null || envAttrs.isEmpty()) {
            envAttrs = pip.getEnvironmentAttributes();
        }
        context.setEnvironment(envAttrs);

        // 构建操作属性
        Map<String, Object> actionAttrs = new HashMap<>();
        actionAttrs.put("action", request.getAction());
        context.setAction(actionAttrs);

        return context;
    }

    private AbacContext convertToAbacContext(PolicyContext context) {
        AbacContext abacContext = new AbacContext();
        abacContext.setSubject(context.getSubject());
        abacContext.setResource(context.getResource());
        abacContext.setAction(context.getAction());
        abacContext.setEnvironment(context.getEnvironment());
        return abacContext;
    }

    private PolicyDecision convertToPolicyDecision(IAbacPermissionChecker.Decision abacDecision) {
        PolicyDecision.Decision decision;
        switch (abacDecision) {
            case PERMIT:
                decision = PolicyDecision.Decision.PERMIT;
                break;
            case DENY:
                decision = PolicyDecision.Decision.DENY;
                break;
            case NOT_APPLICABLE:
            default:
                decision = PolicyDecision.Decision.NOT_APPLICABLE;
                break;
        }
        return new PolicyDecision(decision);
    }

    private void addObligations(PolicyDecision decision, AccessRequest request, PolicyContext context) {
        // TODO: 根据策略规则添加义务（如数据脱敏、审计日志等）
        // 示例：如果需要脱敏，添加脱敏义务
        // decision.addObligation(new PolicyDecision.Obligation("mask", "DATA_MASKING", "{\"fields\":[\"phone\",\"email\"]}"));
    }
}

