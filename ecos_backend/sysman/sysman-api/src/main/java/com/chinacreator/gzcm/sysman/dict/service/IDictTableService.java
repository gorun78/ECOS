package com.chinacreator.gzcm.sysman.dict.service;

import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;

import java.util.List;
import java.util.Map;

public interface IDictTableService {

    List<DictTable> listTables(String schema, String status, String search);

    DictTable getTable(String id);

    DictTable createTable(DictTable table);

    DictTable updateTable(String id, DictTable table);

    void deleteTable(String id);

    List<DictColumn> listColumns(String tableId);

    DictColumn createColumn(String tableId, DictColumn column);

    DictColumn updateColumn(String tableId, String columnId, DictColumn column);

    void deleteColumn(String tableId, String columnId);

    void reorderColumns(String tableId, List<String> columnIds);
}
