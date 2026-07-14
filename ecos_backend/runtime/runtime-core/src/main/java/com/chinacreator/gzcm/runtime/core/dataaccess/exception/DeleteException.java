package com.chinacreator.gzcm.runtime.core.dataaccess.exception;

import com.chinacreator.gzcm.runtime.core.dataaccess.DataAccess.DataAccessException;

/**
 * 鍒犻櫎寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class DeleteException extends DataAccessException {
    
    private static final long serialVersionUID = 1L;
    
    public DeleteException(String message) {
        super(DataAccessErrorCode.DELETE_FAILED.getCode(), message);
    }
    
    public DeleteException(String message, Throwable cause) {
        super(DataAccessErrorCode.DELETE_FAILED.getCode(), message, cause);
    }
    
    public DeleteException(DataAccessErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
    }
    
    public DeleteException(DataAccessErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getCode(), message, cause);
    }
}

