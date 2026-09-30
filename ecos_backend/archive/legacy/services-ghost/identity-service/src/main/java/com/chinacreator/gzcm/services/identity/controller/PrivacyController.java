package com.chinacreator.gzcm.services.identity.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.services.identity.service.PrivacyService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @PostMapping("/export")
    public ApiResponse exportUserData(@RequestParam String userId) {
        return ApiResponse.success(privacyService.exportUserData(authenticatedSubject(userId)));
    }

    @PostMapping("/delete")
    public ApiResponse deleteUserData(@RequestParam String userId) {
        privacyService.deleteUserData(authenticatedSubject(userId));
        return ApiResponse.success("User data anonymized and associations removed");
    }

    // userId 入参仅保留以兼容既有签名（铁律「API 只增不改」），值一律忽略：个人数据的导出/删除主体
    // 必须来自已认证 token，否则任意人可删/导他人全量个人数据（PMO-74 N1）。
    private String authenticatedSubject(String requestedUserId) {
        String subject = UserContext.getCurrentUserId();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException("隐私操作需已认证主体");
        }
        return subject;
    }
}
