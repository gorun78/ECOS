package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * Compatibility transaction boundary adapter.
 * Business modules should depend on this adapter instead of direct TransactionManager usage.
 */
public class LegacyTxBoundary {

    private final TransactionManager delegate;

    public LegacyTxBoundary() {
        this.delegate = new TransactionManager();
    }

    public void begin() {
        delegate.begin();
    }

    public void commit() {
        delegate.commit();
    }

    public void rollback() {
        delegate.rollback();
    }
}
