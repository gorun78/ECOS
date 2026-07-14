package com.chinacreator.gzcm.runtime.core.metadata.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.metadata.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.metadata.model.DataSourceMetadata;
import com.chinacreator.gzcm.runtime.core.metadata.model.TechnicalMetadata;

/**
 * 鎶借薄鐨勮繙绋嬪厓鏁版嵁鏈嶅姟鎺ュ彛銆?
 * <p>
 * 杩愯鏃堕€氳繃璇ユ帴鍙ｈ闂閮ㄥ厓鏁版嵁鏈嶅姟锛堜緥濡?Dc-Cheng锛夛紝
 * 閬垮厤鍦ㄧ紪璇戞湡鐩存帴渚濊禆 DataCenter 妯″潡鐨勫叿浣撳疄鐜般€?
 */
public interface IRemoteMetadataService {

    TechnicalMetadata getTechnicalMetadata(String metadataId) throws Exception;

    DataSchema getSchema(String schemaId) throws Exception;

    DataSourceMetadata getDataSourceMetadata(String datasourceId) throws Exception;

    Map<String, TechnicalMetadata> batchGetTechnicalMetadata(List<String> metadataIds) throws Exception;
}


