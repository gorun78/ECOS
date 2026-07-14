package com.chinacreator.gzcm.runtime.core.dataaccess.exception;

import com.chinacreator.gzcm.runtime.core.dataaccess.DataAccess.DataAccessException;

/**
 * 鏌ヨ寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class QueryException extends DataAccessException {
    
    private static final long serialVersionUID = 1L;
    
    public QueryException(String message) {
        super(DataAccessErrorCode.QUERY_FAILED.getCode(), message);
    }
    
    public QueryException(String message, Throwable cause) {
        super(DataAccessErrorCode.QUERY_FAILED.getCode(), message, cause);
    }
    
    public QueryException(DataAccessErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
    }
    
    public QueryException(DataAccessErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getCode(), message, cause);
    }
}

