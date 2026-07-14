package com.chinacreator.gzcm.runtime.core.kettle.adapter;

import com.chinacreator.gzcm.runtime.core.kettle.KettleException;
import com.chinacreator.gzcm.runtime.core.kettle.model.KettleStep;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;

/**
 * Kettle濮濄儵顎冮柅鍌炲帳閸ｃ劍甯撮崣?
 * 鐠愮喕鐭楃亸鍜緀ttle濮濄儵顎冩潪顒佸床娑撳搫鍞撮柈銊ф畱TransformStep
 * 
 * @author CDRC Runtime Team
 */
public interface KettleStepAdapter {
    
    /**
     * 闁倿鍘ettle濮濄儵顎?
     * 
     * @param kettleStep Kettle濮濄儵顎?
     * @return TransformStep
     * @throws KettleException
     */
    TransformStep adapt(KettleStep kettleStep) throws KettleException;
    
    /**
     * 閼惧嘲褰囬弨顖涘瘮閻ㄥ嚚ettle濮濄儵顎冪猾璇茬€?
     * 
     * @return 濮濄儵顎冪猾璇茬€烽崥宥囆?
     */
    String getSupportedStepType();
}

