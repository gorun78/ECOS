package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IWarnLogService;
import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.util.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 告警日志服务实现
 *
 * <p><b>PMO-59 P4a（告警落库通道）</b>：在保留既有内存态 {@code logStore}（查询语义零变化）的
 * 前提下 <b>只增持久化分支</b> —— 经构造器注入 {@link JdbcTemplate} 后，告警写入/结果回写/处理标记
 * 同步落 {@code ecos_warn_log}（DDL V131），关闭 P2b「告警内存态，进程重启即失」残留风险；
 * 未注入数据源（无参构造）时行为与改造前完全一致。落库异常一律 try/catch 打 WARN
 * <b>不抛出</b>（不阻塞主流程；铁律 §2.5 监控告警收敛 runtime-monitor）。</p>
 *
 * <p>数据源由外部装配注入（网关/宿主应用），本类不 new 任何连接，禁止反向依赖业务模块。</p>
 *
 * @author CDRC Runtime Team
 */
public class WarnLogServiceImpl implements IWarnLogService {

    private static final Logger log = LoggerFactory.getLogger(WarnLogServiceImpl.class);

    /** 落库幂等：同一 log_id 只留痕一行（重复投递不重复插入） */
    private static final String INSERT_SQL =
        "INSERT INTO ecos_warn_log (id, log_id, warn_type, warn_level, warn_objid, warn_objname, "
            + "warn_message, fault_context, review_tag, warn_hand, warn_result, ishanded, warn_time, "
            + "create_by, update_by) VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?,?) "
            + "ON CONFLICT (log_id) DO NOTHING";

    private static final String UPDATE_RESULT_SQL =
        "UPDATE ecos_warn_log SET warn_result = ?, update_time = NOW() WHERE log_id = ? AND is_deleted = 0";

    private static final String UPDATE_HANDED_SQL =
        "UPDATE ecos_warn_log SET ishanded = '1', update_time = NOW() WHERE log_id = ? AND is_deleted = 0";

    // 内存存储：logid -> WarnLogBean
    private final Map<String, WarnLogBean> logStore = new ConcurrentHashMap<>();

    /** 告警落库通道（null = 纯内存态，兼容既有无数据源装配场景） */
    private final JdbcTemplate jdbcTemplate;

    /** 故障上下文序列化器（fault_context 列 JSONB 承接） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 纯内存态构造（既有装配方式，行为与 PMO-59 P4a 前一致）。 */
    public WarnLogServiceImpl() {
        this(null);
    }

    /** 落库态构造：注入数据源访问通道后，告警同步持久化（异常降级 WARN 不阻塞）。 */
    public WarnLogServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageInfo<WarnLogBean> findByPage(Integer offset, Integer pageSize, WarnLogBean condition) throws Exception {
        List<WarnLogBean> all = new ArrayList<>(logStore.values());
        List<WarnLogBean> filtered = all;

        if (condition != null) {
            filtered = all.stream()
                .filter(log -> {
                    if (condition.getWarn_type() != null && !log.getWarn_type().equals(condition.getWarn_type())) {
                        return false;
                    }
                    if (condition.getWarn_objid() != null && !log.getWarn_objid().equals(condition.getWarn_objid())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }

        int start = offset != null ? offset : 0;
        int size = pageSize != null ? pageSize : 10;
        int end = Math.min(start + size, filtered.size());
        List<WarnLogBean> datas = new ArrayList<>();
        for (int i = start; i < end; i++) {
            datas.add(filtered.get(i));
        }

        // 计算页码 (offset从0开始)
        int pageNum = (start / size) + 1;
        PageInfo<WarnLogBean> pageInfo = new PageInfo<>(datas, filtered.size(), pageNum, size);

        return pageInfo;
    }

    @Override
    public void insertWarnLog(WarnLogBean log) throws Exception {
        if (log.getLog_id() == null) {
            log.setLog_id("log_" + System.currentTimeMillis());
        }
        if (log.getWarn_time() == null) {
            log.setWarn_time(new java.sql.Timestamp(System.currentTimeMillis()));
        }
        logStore.put(log.getLog_id(), log);
        persistInsert(log);
    }

    @Override
    public void updateLogResult(String logid, String msg) throws Exception {
        WarnLogBean log = logStore.get(logid);
        if (log != null) {
            log.setWarn_result(msg);
        }
        persistUpdate(UPDATE_RESULT_SQL, msg, logid);
    }

    @Override
    public void setLogHanded(String ids) throws Exception {
        if (ids != null) {
            String[] idArray = ids.split(",");
            for (String id : idArray) {
                WarnLogBean log = logStore.get(id.trim());
                if (log != null) {
                    log.setIshanded("1");
                }
                persistUpdate(UPDATE_HANDED_SQL, id.trim());
            }
        }
    }

    @Override
    public void warn(String type, String objid, String objname, String err, String typeHander) throws Exception {
        insertWarnLog(buildWarnLog(type, objid, objname, err, typeHander, null, null));
    }

    /**
     * 告警（携带结构化故障上下文与复盘标签）— PMO-59 P4a 覆写：上下文与标签随告警一并落库。
     */
    @Override
    public void warn(String type, String objid, String objname, String err, String typeHander,
                     Map<String, Object> faultContext, String reviewTag) throws Exception {
        insertWarnLog(buildWarnLog(type, objid, objname, err, typeHander, faultContext, reviewTag));
    }

    /** 组装告警对象（logid 按毫秒时间戳生成，与既有语义一致）。 */
    private WarnLogBean buildWarnLog(String type, String objid, String objname, String err, String typeHander,
                                     Map<String, Object> faultContext, String reviewTag) {
        WarnLogBean bean = new WarnLogBean();
        bean.setLog_id("log_" + System.currentTimeMillis());
        bean.setWarn_type(type);
        bean.setWarn_objid(objid);
        bean.setWarn_objname(objname);
        bean.setWarn_message(err);
        bean.setWarn_hand(typeHander);
        bean.setIshanded("0");
        bean.setWarn_level("WARN");
        bean.setFault_context(faultContext);
        bean.setReview_tag(reviewTag);
        bean.setWarn_time(new java.sql.Timestamp(System.currentTimeMillis()));
        return bean;
    }

    /** 落库插入（内存态 + 持久化分支；异常降级 WARN 不抛出）。 */
    private void persistInsert(WarnLogBean row) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(INSERT_SQL,
                "warn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                row.getLog_id(),
                row.getWarn_type(),
                row.getWarn_level() == null ? "WARN" : row.getWarn_level(),
                row.getWarn_objid(),
                row.getWarn_objname(),
                row.getWarn_message(),
                toJson(row.getFault_context()),
                row.getReview_tag(),
                row.getWarn_hand(),
                row.getWarn_result(),
                row.getIshanded() == null ? "0" : row.getIshanded(),
                row.getWarn_time() == null
                    ? new java.sql.Timestamp(System.currentTimeMillis()) : row.getWarn_time(),
                "system", "system");
        } catch (Exception e) {
            log.warn("告警落库失败 (ignored, 不阻塞主流程): logId={} type={} err={}",
                row.getLog_id(), row.getWarn_type(), e.getMessage());
        }
    }

    /** 落库更新（单条参数化语句；异常降级 WARN 不抛出）。 */
    private void persistUpdate(String sql, Object... args) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(sql, args);
        } catch (Exception e) {
            log.warn("告警落库更新失败 (ignored, 不阻塞主流程): sql={} err={}", sql, e.getMessage());
        }
    }

    /** 故障上下文序列化（非法/空值置 null；异常降级 WARN 不抛出）。 */
    private String toJson(Map<String, Object> faultContext) {
        if (faultContext == null || faultContext.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(faultContext);
        } catch (Exception e) {
            log.warn("告警 faultContext 序列化失败 (ignored, 该列置空): err={}", e.getMessage());
            return null;
        }
    }
}
