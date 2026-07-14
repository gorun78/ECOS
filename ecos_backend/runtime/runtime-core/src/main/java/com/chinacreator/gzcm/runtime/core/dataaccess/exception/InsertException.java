package com.chinacreator.gzcm.runtime.core.dataaccess.exception;

import com.chinacreator.gzcm.runtime.core.dataaccess.DataAccess.DataAccessException;

/**
 * 鎻掑叆寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class InsertException extends DataAccessException {
    
    private static final long serialVersionUID = 1L;
    
    public InsertException(String message) {
        super(DataAccessErrorCode.INSERT_FAILED.getCode(), message);
    }
    
    public InsertException(String message, Throwable cause) {
        super(DataAccessErrorCode.INSERT_FAILED.getCode(), message, cause);
    }
    
    public InsertException(DataAccessErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
    }
    
    public InsertException(DataAccessErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getCode(), message, cause);
    }
}

