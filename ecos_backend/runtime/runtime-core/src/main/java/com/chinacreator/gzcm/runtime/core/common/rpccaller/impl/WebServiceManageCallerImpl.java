package com.chinacreator.gzcm.runtime.core.common.rpccaller.impl;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestHeader;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.RestfulRequestParameter;
import com.chinacreator.gzcm.runtime.core.common.rpccaller.IWebServiceManageCaller;
import com.chinacreator.gzcm.runtime.core.common.rpccaller.bean.RowFieldInfoAndData;

import java.util.List;

public class WebServiceManageCallerImpl implements IWebServiceManageCaller {

    @Override
    public String callWebService(String url, String method, String params) throws Exception {
        // 占位实现
        return "";
    }

    @Override
    public String getToken(String nodeId, String url, String method,
                           String clientId, String clientSecret,
                           String username, String password,
                           String scope) throws Exception {
        // 占位实现
        return "";
    }

    @Override
    public RowFieldInfoAndData previewRestfulData(String nodeId, String requestUrl,
                                                  String method,
                                                  List<RestfulRequestParameter> params,
                                                  List<RestfulRequestHeader> headers,
                                                  String entityType, String bodyTemplate,
                                                  String encoding, int timeout,
                                                  String resultPath,
                                                  String[] fieldJsonPaths,
                                                  int limit) throws Exception {
        // 占位实现
        return new RowFieldInfoAndData();
    }
}
