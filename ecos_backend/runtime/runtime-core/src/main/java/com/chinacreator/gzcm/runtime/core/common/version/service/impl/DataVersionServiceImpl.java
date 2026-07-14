package com.chinacreator.gzcm.runtime.core.common.version.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.common.version.bean.Version;
import com.chinacreator.gzcm.runtime.core.common.version.service.IDataVersionService;
import com.chinacreator.gzcm.runtime.core.common.version.service.RemoveVersionHistoryBeforeCallback;

/**
 * 数据版本服务实现
 */
@Service
public class DataVersionServiceImpl implements IDataVersionService {
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String UPDATE_RESOURCE_TYPE_VERSION_STATUS = "update TB_RESOURCE_TYPE set VERSION_STATUS = :status where CODE = :code";
    private static final String SELECT_VERSION_STATUS = "select VERSION_STATUS from TB_RESOURCE_TYPE where CODE = :code";
    private static final String SELECT_VERSION_BY_ID = "select V_ID as id, V_GROUP_ID as groupId, V_GROUP_NAME as groupName, V_CODE as name, V_ENABLED as status, V_CREATOR as creator, V_CREATETIME as createDate from TD_DX_DATAVERSION where V_ID = :id";
    private static final String SELECT_VERSION_BY_GROUP_ID = "select V_ID as id, V_GROUP_ID as groupId, V_GROUP_NAME as groupName, V_CODE as name, V_ENABLED as status, V_CREATOR as creator, V_CREATETIME as createDate from TD_DX_DATAVERSION where V_GROUP_ID = :groupId order by V_CODE desc";
    private static final String INSERT_VERSION = "insert into TD_DX_DATAVERSION(V_ID, V_GROUP_ID, V_GROUP_NAME, V_CODE, V_ENABLED, V_CREATOR, V_CREATETIME) values(:id, :groupId, :groupName, :name, :status, :creator, :createDate)";
    private static final String UPDATE_ENABLED_BY_GROUP_AND_NOT_ID = "update TD_DX_DATAVERSION set V_ENABLED = :status where V_GROUP_ID = :groupId and V_ID <> :id";
    private static final String DELETE_VERSION_BY_GROUP_ID = "delete from TD_DX_DATAVERSION where V_GROUP_ID = :groupId";
    
    @Override
    public boolean checkEnabledVersion(String resourceCode) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("code", resourceCode);
            String status = jdbcTemplate.queryForObject(SELECT_VERSION_STATUS, params, String.class);
            return status != null && "1".equals(status);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Version getVersionById(String versionId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("id", versionId);
            return jdbcTemplate.queryForObject(SELECT_VERSION_BY_ID, params, new BeanPropertyRowMapper<>(Version.class));
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public Version addVersion(String groupId, String groupName, String creator) {
        try {
            Version version = new Version();
            version.setId(UUID.randomUUID().toString());
            version.setGroupId(groupId);
            version.setGroupName(groupName);
            version.setName(generateVersionName(groupId));
            version.setStatus("1");
            version.setCreator(creator);
            version.setCreateDate(new Date());
            
            Map<String, Object> params = new HashMap<>();
            params.put("id", version.getId());
            params.put("groupId", version.getGroupId());
            params.put("groupName", version.getGroupName());
            params.put("name", version.getName());
            params.put("status", version.getStatus());
            params.put("creator", version.getCreator());
            params.put("createDate", version.getCreateDate());
            
            jdbcTemplate.update(INSERT_VERSION, params);
            
            // 禁用同组其他版本
            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("status", "0");
            updateParams.put("groupId", groupId);
            updateParams.put("id", version.getId());
            jdbcTemplate.update(UPDATE_ENABLED_BY_GROUP_AND_NOT_ID, updateParams);
            
            return version;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add version", e);
        }
    }
    
    @Override
    public void removeVersionHistory(String versionId, RemoveVersionHistoryBeforeCallback callback) {
        try {
            if (callback != null) {
                callback.beforeRemove(versionId);
            }
            Version version = getVersionById(versionId);
            if (version != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("groupId", version.getGroupId());
                jdbcTemplate.update(DELETE_VERSION_BY_GROUP_ID, params);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove version history", e);
        }
    }
    
    /**
     * 生成版本名称
     * @param groupId 组ID
     * @return 版本名称
     */
    private String generateVersionName(String groupId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            List<Version> versions = jdbcTemplate.query(SELECT_VERSION_BY_GROUP_ID, params, new BeanPropertyRowMapper<>(Version.class));
            int maxVersion = 0;
            for (Version v : versions) {
                try {
                    int versionNum = Integer.parseInt(v.getName());
                    if (versionNum > maxVersion) {
                        maxVersion = versionNum;
                    }
                } catch (NumberFormatException e) {
                    // 忽略非数字版本名
                }
            }
            return String.valueOf(maxVersion + 1);
        } catch (Exception e) {
            return "1";
        }
    }
}
