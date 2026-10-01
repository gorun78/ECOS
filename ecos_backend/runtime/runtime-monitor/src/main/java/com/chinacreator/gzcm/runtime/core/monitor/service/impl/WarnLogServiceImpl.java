package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IWarnLogService;
import com.chinacreator.gzcm.runtime.core.monitor.server.warn.dao.WarnLogDao;
import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.util.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 告警日志服务实现
 *
 * <p><b>PMO-59 P4a（告警落库通道）</b>：在保留既有内存态 {@code logStore}（查询语义零变化）的
 * 前提下 <b>只增持久化分支</b> —— 经构造器注入可选 {@link WarnLogDao} 后，告警写入/结果回写/处理标记
 * 同步落 {@code ecos_warn_log}（DDL V131），关闭 P2b「告警内存态，进程重启即失」残留风险；
 * 未注入落库通道（无参构造）时行为与改造前完全一致。落库异常一律 try/catch 打 WARN
 * <b>不抛出</b>（不阻塞主流程；铁律 §2.5 监控告警收敛 runtime-monitor）。</p>
 *
 * <p>R2-B4c 数据访问收口：raw-SQL 一律下沉 {@link WarnLogDao}（..server.warn.dao..，继承
 * runtime-core {@code JdbcTemplateDaoSupport}），本 Service 退化为纯内存态门面 + 经 DAO 落库，
 * 不再私持数据访问对象。数据源由外部装配注入（网关/宿主应用），本类不 new 任何连接，
 * 禁止反向依赖业务模块。</p>
 *
 * @author CDRC Runtime Team
 */
public class WarnLogServiceImpl implements IWarnLogService {

    private static final Logger log = LoggerFactory.getLogger(WarnLogServiceImpl.class);

    // 内存存储：logid -> WarnLogBean
    private final Map<String, WarnLogBean> logStore = new ConcurrentHashMap<>();

    /** 告警落库通道（null = 纯内存态，兼容既有无数据源装配场景） */
    private final WarnLogDao warnLogDao;

    /** 纯内存态构造（既有装配方式，行为与 PMO-59 P4a 前一致）。 */
    public WarnLogServiceImpl() {
        this(null);
    }

    /** 落库态构造：注入下沉 DAO 通道后，告警同步持久化（异常降级 WARN 不阻塞）。 */
    public WarnLogServiceImpl(WarnLogDao warnLogDao) {
        this.warnLogDao = warnLogDao;
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
    public void insertWarnLog(WarnLogBean logBean) throws Exception {
        if (logBean.getLog_id() == null) {
            logBean.setLog_id("log_" + System.currentTimeMillis());
        }
        if (logBean.getWarn_time() == null) {
            logBean.setWarn_time(new java.sql.Timestamp(System.currentTimeMillis()));
        }
        logStore.put(logBean.getLog_id(), logBean);
        persistInsert(logBean);
    }

    @Override
    public void updateLogResult(String logid, String msg) throws Exception {
        WarnLogBean log = logStore.get(logid);
        if (log != null) {
            log.setWarn_result(msg);
        }
        persistUpdate(WarnLogDao.UPDATE_RESULT_SQL, msg, logid);
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
                persistUpdate(WarnLogDao.UPDATE_HANDED_SQL, id.trim());
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
        if (warnLogDao == null) {
            return;
        }
        try {
            warnLogDao.insert(row);
        } catch (Exception e) {
            log.warn("告警落库失败 (ignored, 不阻塞主流程): logId={} type={} err={}",
                row.getLog_id(), row.getWarn_type(), e.getMessage());
        }
    }

    /** 落库更新（单条参数化语句；异常降级 WARN 不抛出）。 */
    private void persistUpdate(String sql, Object... args) {
        if (warnLogDao == null) {
            return;
        }
        try {
            warnLogDao.update(sql, args);
        } catch (Exception e) {
            log.warn("告警落库更新失败 (ignored, 不阻塞主流程): sql={} err={}", sql, e.getMessage());
        }
    }
}
