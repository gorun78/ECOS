package com.chinacreator.gzcm.common.service;

import java.util.List;
import java.util.Optional;

/**
 * 对象版本归档端口（DS-4 对象运行时域；2026-11-08）。
 *
 * <p><b>为什么是端口而不是直接依赖 Git</b>：对象运行时的 DAO/Service 位于 {@code common-api}
 * （最底层，禁反向依赖 {@code runtime-access}），而消费方在 workspace。由上层模块实现本端口，
 * 经 {@code ObjectProvider} 注入；未装配时保留库内回退（测试/未接 Git 的进程）。</p>
 *
 * <p>实现方要求：提交即可见（版本真相），读不到必须显式空/异常，禁止把「没有归档」读成「没有版本」。</p>
 */
public interface ObjectVersionArchivePort {

    /** 写入一条对象版本快照（幂等：同对象同版本号覆盖为一次新提交）。 */
    Optional<String> archive(String objectId, String entityCode, int versionNo,
                             String snapshotJson, String changeSummary, String actor);

    /** 某对象的版本索引（新→旧，不含 snapshot）。 */
    List<ObjectVersionRecord> list(String objectId);

    /** 单版本（含 snapshot）；不存在返回空。 */
    Optional<ObjectVersionRecord> get(String objectId, int versionNo);

    /** 当前最大版本号（无版本返回 0）。 */
    int maxVersion(String objectId);

    /** 版本记录（id 为 {@code git-<commitId>}；snapshot 仅在 get 时填充）。 */
    record ObjectVersionRecord(String id, String objectId, String entityCode, int versionNo,
                               String snapshot, String changeSummary, String createdBy,
                               Long createdAt) {
    }
}
