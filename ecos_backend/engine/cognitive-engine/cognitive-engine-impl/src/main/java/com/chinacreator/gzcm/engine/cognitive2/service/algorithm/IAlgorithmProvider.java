package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.Map;

/** 执行器集合提供者：按类分文件组织，service 聚合全部 provider。 */
public interface IAlgorithmProvider {

    Map<String, IAlgorithmExecutor> executors();
}
