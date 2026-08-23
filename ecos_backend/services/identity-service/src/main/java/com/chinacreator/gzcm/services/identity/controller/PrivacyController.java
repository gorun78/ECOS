package com.chinacreator.gzcm.services.identity.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.identity.service.PrivacyService;
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
        return ApiResponse.success(privacyService.exportUserData(userId));
    }

    @PostMapping("/delete")
    public ApiResponse deleteUserData(@RequestParam String userId) {
        privacyService.deleteUserData(userId);
        return ApiResponse.success("User data anonymized and associations removed");
    }
}
