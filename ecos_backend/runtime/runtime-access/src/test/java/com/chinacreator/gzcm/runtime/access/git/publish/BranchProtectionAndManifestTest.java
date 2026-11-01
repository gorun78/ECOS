package com.chinacreator.gzcm.runtime.access.git.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** §2.9.9④ 分支保护与签名发布清单契约（#147F）。 */
class BranchProtectionAndManifestTest {

    private static PublishManifest manifest(String signature) {
        return new PublishManifest("ontology-model", "abc123", "sha256:deadbeef",
                "enterprise", "approval-77", "policy-v3", signature);
    }

    @Test
    void manifestBindsSixFieldsAndCommitShaAloneIsNotAuthorization() {
        assertTrue(manifest(null).isComplete());
        assertFalse(new PublishManifest("ontology-model", "abc123", null, null, null, null, null).isComplete());
        assertEquals(List.of("contentHash", "targetEnvironment", "approvalRecordId", "policyVersion"),
                new PublishManifest("ontology-model", "abc123", null, null, null, null, null).missingFields());

        IllegalStateException ex = assertThrows(IllegalArgumentException.class,
                () -> new PublishManifest("ontology-model", "abc123", null, null, null, null, null)
                        .assertAuthorizesPublish(false));
        assertTrue(ex.getMessage().contains("禁止发布"));
    }

    @Test
    void signatureRequirementIsEnforcedWhenPolicyDemands() {
        manifest(null).assertAuthorizesPublish(false);
        IllegalStateException ex = assertThrows(IllegalArgumentException.class,
                () -> manifest(null).assertAuthorizesPublish(true));
        assertTrue(ex.getMessage().contains("Commit SHA ≠ 发布授权"));
        manifest("sig:xyz").assertAuthorizesPublish(true);
    }

    @Test
    void protectedBranchForbidsForcePushAndDirectPushWithoutManifest() {
        BranchProtectionPolicy policy = BranchProtectionPolicy.strictDefault();
        assertEquals(Set.of("main"), policy.protectedBranches());

        assertTrue(policy.checkPush("main", true, manifest(null)).contains(BranchProtectionPolicy.Violation.FORCE_PUSH_FORBIDDEN));
        assertTrue(policy.checkPush("main", false, null).contains(BranchProtectionPolicy.Violation.DIRECT_PUSH_FORBIDDEN));
        assertTrue(policy.checkPush("main", false, manifest(null)).isEmpty(), "完整清单允许");
        assertTrue(policy.checkPush("feature/x", false, null).isEmpty(), "非保护分支可直推");
    }

    @Test
    void deniedWhenManifestIncompleteOrSigningRequiredWithoutInfrastructure() {
        BranchProtectionPolicy signing = new BranchProtectionPolicy(Set.of("main"), true, true);
        List<BranchProtectionPolicy.Violation> violations = signing.checkPush("main", false, manifest(null));
        assertTrue(violations.contains(BranchProtectionPolicy.Violation.SIGNATURE_REQUIRED));
        assertTrue(violations.contains(BranchProtectionPolicy.Violation.SIGNING_NOT_AVAILABLE));

        assertThrows(IllegalArgumentException.class, () -> signing.assertPublishAllowed(manifest(null)));
        assertThrows(IllegalArgumentException.class, () -> signing.assertPublishAllowed(null));
    }
}
