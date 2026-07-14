package com.chinacreator.gzcm.runtime.core.crypto.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.chinacreator.gzcm.runtime.core.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;

public class DataEncryptionServiceImpl implements IDataEncryptionService {

    private final IKeyManagementService keyService;

    public DataEncryptionServiceImpl(IKeyManagementService keyService) {
        this.keyService = keyService;
    }

    private Cipher cipher(String mode, String keyId) throws Exception {
        byte[] keyBytes = keyService.getKeyBytes(keyId);
        if (keyBytes == null) {
            keyService.createKey(keyId, "AES", 128);
            keyBytes = keyService.getKeyBytes(keyId);
        }
        SecretKeySpec spec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init("encrypt".equals(mode) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, spec);
        return cipher;
    }

    @Override
    public String encrypt(String data, String keyId) throws EncryptionException {
        try {
            byte[] encrypted = encrypt(data.getBytes(StandardCharsets.UTF_8), keyId);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new EncryptionException("ENC", "encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedData, String keyId) throws EncryptionException {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            byte[] plain = decrypt(decoded, keyId);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("DEC", "decrypt failed", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] data, String keyId) throws EncryptionException {
        try {
            return cipher("encrypt", keyId).doFinal(data);
        } catch (Exception e) {
            throw new EncryptionException("ENC", "encrypt failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] encryptedData, String keyId) throws EncryptionException {
        try {
            return cipher("decrypt", keyId).doFinal(encryptedData);
        } catch (Exception e) {
            throw new EncryptionException("DEC", "decrypt failed", e);
        }
    }

    @Override
    public <T> T encryptField(T data, String fieldName, String keyId) throws EncryptionException {
        return data;
    }

    @Override
    public <T> T decryptField(T data, String fieldName, String keyId) throws EncryptionException {
        return data;
    }

    @Override
    public List<String> encryptBatch(List<String> dataList, String keyId) throws EncryptionException {
        return dataList.stream().map(d -> {
            try {
                return encrypt(d, keyId);
            } catch (EncryptionException e) {
                return null;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> decryptBatch(List<String> encryptedDataList, String keyId) throws EncryptionException {
        return encryptedDataList.stream().map(d -> {
            try {
                return decrypt(d, keyId);
            } catch (EncryptionException e) {
                return null;
            }
        }).collect(Collectors.toList());
    }
}

