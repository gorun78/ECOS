package com.chinacreator.gzcm.runtime.core.dataaccess.impl;

import java.util.Collections;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.dataaccess.DataAccess;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.BatchRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.BatchResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.DeleteRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.InsertOptions;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.UpdateRequest;

/**
 * Minimal in-memory DataAccess implementation returning empty results.
 */
public class DataAccessImpl implements DataAccess {

    @Override
    public <T> QueryResult<T> query(QueryRequest request, Class<T> clazz) throws DataAccessException {
        return new QueryResult<>(Collections.emptyList(), 0L);
    }

    @Override
    public <T> void insert(String dataProductId, List<T> data, InsertOptions options) throws DataAccessException {
        // no-op
    }

    @Override
    public <T> int update(UpdateRequest request, T data) throws DataAccessException {
        return 0;
    }

    @Override
    public int delete(DeleteRequest request) throws DataAccessException {
        return 0;
    }

    @Override
    public <T> BatchResult batch(BatchRequest<T> request) throws DataAccessException {
        return new BatchResult();
    }
}

