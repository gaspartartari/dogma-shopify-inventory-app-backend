package com.tartaritech.inventory_sync.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class HmacMd5Generator {

    public static String generateHmacMD5(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacMD5");
            mac.init(secretKey);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw)
                sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-MD5", e);
        }
    }
}
