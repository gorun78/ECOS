package com.chinacreator.gzcm.sysman.dict.service;

import com.chinacreator.gzcm.sysman.dict.entity.SysDict;

import java.util.List;
import java.util.Map;

/**
 * 数据字典服务接口
 */
public interface IDictService {

    /**
     * 获取所有字典类型列表
     */
    List<String> listDictTypes();

    /**
     * 获取指定类型的字典项（优先从缓存读取）
     */
    List<SysDict> getDictItems(String dictType);

    /**
     * 获取指定类型的单个字典项
     */
    SysDict getDictItem(String dictType, String dictCode);

    /**
     * 创建字典项
     */
    SysDict createDictItem(SysDict dict);

    /**
     * 更新字典项
     */
    SysDict updateDictItem(String dictType, String dictCode, SysDict dict);

    /**
     * 软删除字典项（设置 status = 'inactive'）
     */
    void deleteDictItem(String dictType, String dictCode);

    /**
     * 刷新缓存
     */
    Map<String, Object> refreshCache();

    /**
     * 字典审计：查询某个字典类型被哪些模块引用
     */
    Map<String, Object> getDictUsage(String dictType);
}
