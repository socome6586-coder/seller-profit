package com.sellerprofit.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 쿠팡 API 키/시크릿을 AES-256-GCM 으로 암복호화.
 * 저장 포맷: [IV(12 byte) || ciphertext+tag] 를 그대로 BYTEA 컬럼에 저장한다.
 *
 * app.encryption.key 는 Base64 인코딩된 32 byte(=AES-256) 키.
 * 반드시 환경변수/시크릿 매니저로 주입하고 소스/깃에 커밋하지 말 것.
 */
@Component
public class AesGcmEncryptor {

    private static final int IV_LENGTH = 12;        // GCM 권장 IV 길이(byte)
    private static final int TAG_LENGTH_BITS = 128; // 인증 태그 길이(bit)

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(@Value("${app.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.encryption.key 는 Base64 인코딩된 32 byte 여야 합니다 (AES-256).");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** 평문 → [IV || ciphertext+tag] */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        } catch (Exception e) {
            // 예외 메시지에 평문/키가 새어나가지 않도록 원인만 감싼다
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /** [IV || ciphertext+tag] → 평문 */
    public String decrypt(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }
}
