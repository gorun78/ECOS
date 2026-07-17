package com.chinacreator.gzcm.services.identity.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public MfaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/totp/setup")
    public ApiResponse setupTotp(@RequestParam String userId) {
        byte[] secretBytes = new byte[20];
        random.nextBytes(secretBytes);
        String base32Secret = base32Encode(secretBytes);

        String otpauthUrl = String.format(
            "otpauth://totp/ECOS:user_%s?secret=%s&issuer=ECOS&algorithm=SHA1&digits=6&period=30",
            userId, base32Secret
        );

        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET mfa_secret = ?, mfa_type = 'TOTP', mfa_enabled = false WHERE id = ?",
            base32Secret, UUID.fromString(userId)
        );

        Map<String, String> result = new HashMap<>();
        result.put("secret", base32Secret);
        result.put("otpauthUrl", otpauthUrl);
        return ApiResponse.success(result);
    }

    @PostMapping("/totp/verify")
    public ApiResponse verifyTotp(@RequestParam String userId, @RequestParam String code) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT mfa_secret, mfa_enabled FROM ecos_identity.td_user WHERE id = ?",
            UUID.fromString(userId)
        );

        String secret = (String) user.get("mfa_secret");
        if (secret == null) {
            return ApiResponse.badRequest("MFA not set up. Call /mfa/totp/setup first.");
        }

        boolean valid = verifyTotpCode(secret, code);
        if (valid) {
            if (!Boolean.TRUE.equals(user.get("mfa_enabled"))) {
                jdbcTemplate.update(
                    "UPDATE ecos_identity.td_user SET mfa_enabled = true WHERE id = ?",
                    UUID.fromString(userId)
                );
            }
            return ApiResponse.success("MFA verification successful");
        }
        return ApiResponse.badRequest("Invalid TOTP code");
    }

    @PostMapping("/disable")
    public ApiResponse disableMfa(@RequestParam String userId, @RequestParam String code) {
        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT mfa_secret, mfa_enabled FROM ecos_identity.td_user WHERE id = ?",
            UUID.fromString(userId)
        );

        String secret = (String) user.get("mfa_secret");
        if (secret == null) return ApiResponse.badRequest("MFA not set up");

        if (verifyTotpCode(secret, code)) {
            jdbcTemplate.update(
                "UPDATE ecos_identity.td_user SET mfa_secret = NULL, mfa_type = NULL, mfa_enabled = false WHERE id = ?",
                UUID.fromString(userId)
            );
            return ApiResponse.success("MFA disabled");
        }
        return ApiResponse.badRequest("Invalid TOTP code");
    }

    private boolean verifyTotpCode(String base32Secret, String code) {
        try {
            byte[] secretBytes = base32Decode(base32Secret);
            long timeStep = System.currentTimeMillis() / 30000;
            for (long i = -1; i <= 1; i++) {
                String generatedCode = generateTotp(secretBytes, timeStep + i);
                if (generatedCode.equals(code)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private String generateTotp(byte[] secret, long timeStep) throws Exception {
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(timeBytes);
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24) |
                     ((hash[offset + 1] & 0xff) << 16) |
                     ((hash[offset + 2] & 0xff) << 8) |
                     (hash[offset + 3] & 0xff);
        int otp = binary % 1000000;
        return String.format("%06d", otp);
    }

    private String base32Encode(byte[] data) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(chars.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(chars.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return result.toString();
    }

    private byte[] base32Decode(String base32) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int[] lookup = new int[256];
        Arrays.fill(lookup, -1);
        for (int i = 0; i < chars.length(); i++) lookup[chars.charAt(i)] = i;

        ByteBuffer buffer = ByteBuffer.allocate(base32.length() * 5 / 8 + 1);
        int accum = 0, bits = 0;
        for (char c : base32.toUpperCase().toCharArray()) {
            if (lookup[c] == -1) continue;
            accum = (accum << 5) | lookup[c];
            bits += 5;
            if (bits >= 8) {
                buffer.put((byte) (accum >> (bits - 8)));
                bits -= 8;
            }
        }
        byte[] result = new byte[buffer.position()];
        System.arraycopy(buffer.array(), 0, result, 0, result.length);
        return result;
    }
}
