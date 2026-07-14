package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PgObjectStorageService — PostgreSQL object storage stub (no-op).
 * Provides a minimal Spring bean for DataLakeExportService DI resolution.
 */
@Service
public class PgObjectStorageService implements IObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(PgObjectStorageService.class);

    @Override
    public String putObject(String key, byte[] data, String contentType) {
        log.debug("PgObjectStorageService.putObject stub: key={}, size={}", key, data != null ? data.length : 0);
        return key;
    }

    @Override
    public byte[] getObject(String key) {
        log.debug("PgObjectStorageService.getObject stub: key={}", key);
        return null;
    }

    @Override
    public void deleteObject(String key) {
        log.debug("PgObjectStorageService.deleteObject stub: key={}", key);
    }
}
