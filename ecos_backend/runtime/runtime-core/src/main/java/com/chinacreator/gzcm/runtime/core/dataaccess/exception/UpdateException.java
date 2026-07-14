package com.chinacreator.gzcm.runtime.core.dataaccess.exception;

import com.chinacreator.gzcm.runtime.core.dataaccess.DataAccess.DataAccessException;

/**
 * 鏇存柊寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class UpdateException extends DataAccessException {
    
    private static final long serialVersionUID = 1L;
    
    public UpdateException(String message) {
        super(DataAccessErrorCode.UPDATE_FAILED.getCode(), message);
    }
    
    public UpdateException(String message, Throwable cause) {
        super(DataAccessErrorCode.UPDATE_FAILED.getCode(), message, cause);
    }
    
    public UpdateException(DataAccessErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
    }
    
    public UpdateException(DataAccessErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.getCode(), message, cause);
    }
}

