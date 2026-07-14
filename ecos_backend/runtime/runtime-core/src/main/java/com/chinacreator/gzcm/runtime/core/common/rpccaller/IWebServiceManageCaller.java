package com.chinacreator.gzcm.runtime.core.common.rpccaller;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestHeader;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestParameter;
import com.chinacreator.gzcm.runtime.core.common.rpccaller.bean.RowFieldInfoAndData;

import java.util.List;

public interface IWebServiceManageCaller {
    /**
     * 最基础的 WebService 调用
     */
    String callWebService(String url, String method, String params) throws Exception;

    /**
     * 兼容旧代码：获取 Token
     */
    default String getToken(String nodeId, String url, String method,
                            String clientId, String clientSecret,
                            String username, String password,
                            String scope) throws Exception {
        // 占位实现，返回空字符串避免编译错误
        return "";
    }

    /**
     * 兼容旧代码：预览 RESTful 数据
     */
    default RowFieldInfoAndData previewRestfulData(String nodeId, String requestUrl,
                                                   String method,
                                                   List<RestfulRequestParameter> params,
                                                   List<RestfulRequestHeader> headers,
                                                   String entityType, String bodyTemplate,
                                                   String encoding, int timeout,
                                                   String resultPath,
                                                   String[] fieldJsonPaths,
                                                   int limit) throws Exception {
        // 占位实现：返回空结果
        return new RowFieldInfoAndData();
    }
}
