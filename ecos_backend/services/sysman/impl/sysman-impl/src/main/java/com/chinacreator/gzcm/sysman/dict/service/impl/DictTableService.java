package com.chinacreator.gzcm.sysman.dict.service.impl;

import com.chinacreator.gzcm.sysman.dao.DictTableDao;
import com.chinacreator.gzcm.sysman.dict.entity.DictColumn;
import com.chinacreator.gzcm.sysman.dict.entity.DictTable;
import com.chinacreator.gzcm.sysman.dict.service.IDictTableService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DictTableService implements IDictTableService {

    private final DictTableDao dao;

    public DictTableService(DictTableDao dao) {
        this.dao = dao;
        // H8-T1: dict_table / dict_column 建表 DDL（含外键与索引）收编至 db/migration（V162），
        // 构造函数不再内嵌 DDL。
    }

    @Override
    public List<DictTable> listTables(String schema, String status, String search) {
        return dao.listTables(schema, status, search);
    }

    @Override
    public DictTable getTable(String id) {
        return dao.getTable(id);
    }

    @Override
    public List<DictColumn> listColumns(String tableId) {
        return dao.listColumns(tableId);
    }

    @Override
    public DictTable createTable(DictTable table) {
        String id = UUID.randomUUID().toString().replace("-", "");
        table.setId(id);
        if (table.getStatus() == null || table.getStatus().isEmpty()) {
            table.setStatus("DRAFT");
        }

        dao.insertTable(id, table);
        return getTable(id);
    }

    @Override
    public DictTable updateTable(String id, DictTable table) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (table.getName() != null) { sets.add("name = ?"); params.add(table.getName()); }
        if (table.getNameZh() != null) { sets.add("name_zh = ?"); params.add(table.getNameZh()); }
        if (table.getDescription() != null) { sets.add("description = ?"); params.add(table.getDescription()); }
        if (table.getStatus() != null) { sets.add("status = ?"); params.add(table.getStatus()); }
        if (table.getSource() != null) { sets.add("source = ?"); params.add(table.getSource()); }
        if (table.getTags() != null) { sets.add("tags = ?"); params.add(table.getTags()); }
        if (table.getCode() != null) { sets.add("code = ?"); params.add(table.getCode()); }
        if (table.getSchema() != null) { sets.add("schema_name = ?"); params.add(table.getSchema()); }
        if (table.getOwner() != null) { sets.add("owner = ?"); params.add(table.getOwner()); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = NOW()");
            params.add(id);
            dao.updateTable(id, sets, params);
        }
        return getTable(id);
    }

    @Override
    public void deleteTable(String id) {
        dao.deleteTable(id);
    }

    @Override
    public DictColumn createColumn(String tableId, DictColumn column) {
        String id = UUID.randomUUID().toString().replace("-", "");
        column.setId(id);
        column.setTableId(tableId);

        Integer maxOrder = dao.maxColumnSortOrder(tableId);
        if (column.getSortOrder() == 0 && maxOrder != null) {
            column.setSortOrder(maxOrder + 1);
        }

        dao.insertColumn(column);
        return dao.getColumnById(id);
    }

    @Override
    public DictColumn updateColumn(String tableId, String columnId, DictColumn column) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (column.getName() != null) { sets.add("name = ?"); params.add(column.getName()); }
        if (column.getType() != null) { sets.add("type = ?"); params.add(column.getType()); }
        if (column.getLength() != null) { sets.add("length = ?"); params.add(column.getLength()); }
        if (column.getPrecision() != null) { sets.add("precision_val = ?"); params.add(column.getPrecision()); }
        if (column.getScale() != null) { sets.add("scale = ?"); params.add(column.getScale()); }
        sets.add("nullable = ?");
        params.add(column.isNullable());
        sets.add("primary_key = ?");
        params.add(column.isPrimaryKey());
        if (column.getDefaultValue() != null) { sets.add("default_value = ?"); params.add(column.getDefaultValue()); }
        if (column.getDescription() != null) { sets.add("description = ?"); params.add(column.getDescription()); }
        if (column.getSortOrder() > 0) { sets.add("sort_order = ?"); params.add(column.getSortOrder()); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = NOW()");
            params.add(columnId);
            params.add(tableId);
            dao.updateColumn(sets, params);
        }
        return dao.getColumnByIdAndTable(columnId, tableId);
    }

    @Override
    public void deleteColumn(String tableId, String columnId) {
        dao.deleteColumn(tableId, columnId);
    }

    @Override
    public void reorderColumns(String tableId, List<String> columnIds) {
        for (int i = 0; i < columnIds.size(); i++) {
            dao.setColumnOrder(i + 1, columnIds.get(i), tableId);
        }
    }
}
