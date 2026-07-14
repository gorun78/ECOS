package com.chinacreator.gzcm.runtime.core.common.params.service;

public interface IParamsHandlerService {
    <T> T findParams(String objectId, Class<T> clazz, String dbName) throws Exception;
    void saveParams(Object param) throws Exception;
    void updateParams(Object param) throws Exception;
}
