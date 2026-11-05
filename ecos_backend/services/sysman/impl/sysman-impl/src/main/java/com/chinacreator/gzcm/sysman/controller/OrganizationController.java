package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 机构 CRUD（{@code /api/v1/system/organizations}）。
 *
 * <p><b>#147CB（DoD DI-2 第七批）</b>：请求体由弱类型 Map 改 {@link OrganizationRequest}，
 * 响应由 {@code toFlatMap}/{@code toTreeMap} 改 {@link OrganizationVO}/{@link OrganizationTreeNodeVO}。
 * wire 键面逐字不变：平铺 10 键；树节点在非空子集时<b>才</b>追加 {@code children}
 * （属性级 NON_NULL，不得未消费也输出空数组）；{@code createdTime} 仍为 {@code LocalDateTime}
 * （同一 ObjectMapper 渲染，禁改成字符串导致时区/格式漂移）；建机构缺省 {@code orgType=DEPARTMENT}
 * 与自动 {@code orgCode=ORG_<millis>} 口径同守。</p>
 */
@RestController
@RequestMapping("/api/v1/system/organizations")
public class OrganizationController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationController.class);
    private final IOrganizationService orgService;

    public OrganizationController(IOrganizationService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/all")
    public ApiResponse<List<OrganizationVO>> all() {
        try {
            List<Organization> orgs = orgService.listOrganizations(null, null);
            return ApiResponse.success(orgs.stream().map(OrganizationController::toVO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询机构列表失败", e);
            return ApiResponse.internalError("查询机构列表失败");
        }
    }

    @GetMapping("/tree")
    public ApiResponse<OrganizationTreeNodeVO> tree(@RequestParam(required = false) String rootOrgId) {
        try {
            Organization root = orgService.getOrganizationTree(rootOrgId);
            return ApiResponse.success(toTreeNodeVO(root));
        } catch (Exception e) {
            log.error("查询机构树失败", e);
            return ApiResponse.internalError("查询机构树失败");
        }
    }

    @GetMapping("/children")
    public ApiResponse<List<OrganizationVO>> children(@RequestParam(required = false) String parentId) {
        try {
            List<Organization> children = orgService.getChildren(parentId);
            return ApiResponse.success(children.stream().map(OrganizationController::toVO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询子机构失败", e);
            return ApiResponse.internalError("查询子机构失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<OrganizationVO> get(@PathVariable String id) {
        try {
            Organization org = orgService.getOrganization(id);
            if (org == null) return ApiResponse.notFound("机构不存在");
            return ApiResponse.success(toVO(org));
        } catch (Exception e) {
            log.error("查询机构详情失败", e);
            return ApiResponse.internalError("查询机构详情失败");
        }
    }

    @PostMapping
    @RequirePermission(permission = "system:org:create")
    public ApiResponse<OrganizationVO> create(@RequestBody OrganizationRequest body) {
        try {
            String orgName = body.getOrgName();
            String orgCode = body.getOrgCode();
            if (orgName == null || orgName.isEmpty()) return ApiResponse.badRequest("机构名称不能为空");
            // Generate unique orgCode if not provided (ORG_CODE has unique constraint)
            if (orgCode == null || orgCode.isEmpty()) {
                orgCode = "ORG_" + System.currentTimeMillis();
            }

            Organization org = orgService.createOrganization(
                orgName,
                orgCode,
                body.getParentOrgId(),
                body.getOrgType() != null ? body.getOrgType() : "DEPARTMENT",
                body.getDescription(),
                "admin"
            );
            return ApiResponse.success(toVO(org));
        } catch (Exception e) {
            log.error("创建机构失败", e);
            return ApiResponse.internalError("创建机构失败");
        }
    }

    @PutMapping("/{id}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<OrganizationVO> update(@PathVariable String id, @RequestBody OrganizationRequest body) {
        try {
            Organization org = orgService.updateOrganization(
                id,
                body.getOrgName(),
                body.getOrgCode(),
                body.getOrgType(),
                body.getDescription(),
                "admin"
            );
            return ApiResponse.success(toVO(org));
        } catch (Exception e) {
            log.error("更新机构失败", e);
            return ApiResponse.internalError("更新机构失败");
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permission = "system:org:delete")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            orgService.deleteOrganization(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除机构失败", e);
            return ApiResponse.internalError("删除机构失败");
        }
    }

    @PutMapping("/{orgId}/users/{userId}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<Void> assignUser(@PathVariable String orgId, @PathVariable String userId,
                                         @RequestParam(defaultValue = "false") boolean primary) {
        try {
            orgService.assignUserToOrg(userId, orgId, primary, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配用户到机构失败", e);
            return ApiResponse.internalError("分配用户到机构失败");
        }
    }

    @DeleteMapping("/{orgId}/users/{userId}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<Void> removeUser(@PathVariable String orgId, @PathVariable String userId) {
        try {
            orgService.removeUserFromOrg(userId, orgId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("移除用户机构关联失败", e);
            return ApiResponse.internalError("移除用户机构关联失败");
        }
    }

    static OrganizationVO toVO(Organization o) {
        return new OrganizationVO(o.getOrgId(), o.getOrgName(), o.getOrgCode(), o.getParentOrgId(),
                o.getOrgType(), o.getDescription(), o.getStatus(), o.getRemark(), o.getPath(),
                o.getCreatedTime());
    }

    static OrganizationTreeNodeVO toTreeNodeVO(Organization o) {
        if (o == null) return null;
        OrganizationVO base = toVO(o);
        List<OrganizationTreeNodeVO> children = o.getChildren() == null || o.getChildren().isEmpty()
                ? null
                : o.getChildren().stream().map(OrganizationController::toTreeNodeVO)
                        .collect(Collectors.toList());
        return new OrganizationTreeNodeVO(base.orgId(), base.orgName(), base.orgCode(),
                base.parentOrgId(), base.orgType(), base.description(), base.status(),
                base.remark(), base.path(), base.createdTime(), children);
    }

    /** 机构建/改请求（create 读 parentOrgId/orgType 缺省，update 只读 orgName/orgCode/orgType/description）。 */
    public static final class OrganizationRequest {
        private String orgName;
        private String orgCode;
        private String parentOrgId;
        private String orgType;
        private String description;

        public String getOrgName() {
            return orgName;
        }

        public void setOrgName(String orgName) {
            this.orgName = orgName;
        }

        public String getOrgCode() {
            return orgCode;
        }

        public void setOrgCode(String orgCode) {
            this.orgCode = orgCode;
        }

        public String getParentOrgId() {
            return parentOrgId;
        }

        public void setParentOrgId(String parentOrgId) {
            this.parentOrgId = parentOrgId;
        }

        public String getOrgType() {
            return orgType;
        }

        public void setOrgType(String orgType) {
            this.orgType = orgType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /** 机构平铺视图（10 键，键序与旧 Map 一致）。 */
    public record OrganizationVO(String orgId, String orgName, String orgCode, String parentOrgId,
                                 String orgType, String description, String status, String remark,
                                 String path, LocalDateTime createdTime) {
    }

    /** 机构树节点（10 键 + 非空子集时追加 {@code children}）。 */
    public record OrganizationTreeNodeVO(String orgId, String orgName, String orgCode,
                                         String parentOrgId, String orgType, String description,
                                         String status, String remark, String path,
                                         LocalDateTime createdTime,
                                         @JsonInclude(JsonInclude.Include.NON_NULL)
                                         List<OrganizationTreeNodeVO> children) {
    }
}
