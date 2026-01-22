package com.example.messagesender.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AESUtils {

  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final String ALGORITHM = "AES";

  private final AESProperties properties;

  public AESUtils(AESProperties properties) {
    this.properties = properties;
  }

  public String encrypt(String plainText) {
    if (plainText == null) {
      return null;
    }

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);

      SecretKeySpec keySpec = new SecretKeySpec(
          properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
          ALGORITHM
      );

      IvParameterSpec ivSpec = new IvParameterSpec(
          properties.getFixedIv().getBytes(StandardCharsets.UTF_8)
      );

      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

      byte[] encrypted = cipher.doFinal(
          plainText.getBytes(StandardCharsets.UTF_8)
      );

      return Base64.getEncoder().encodeToString(encrypted);

    } catch (Exception e) {
      throw new IllegalStateException("AES 암호화 실패", e);
    }
  }

  public String decrypt(String encryptedText) {
    if (encryptedText == null) {
      return null;
    }

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);

      SecretKeySpec keySpec = new SecretKeySpec(
          properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
          ALGORITHM
      );

      IvParameterSpec ivSpec = new IvParameterSpec(
          properties.getFixedIv().getBytes(StandardCharsets.UTF_8)
      );

      cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

      byte[] decoded = Base64.getDecoder().decode(encryptedText);
      byte[] decrypted = cipher.doFinal(decoded);

      return new String(decrypted, StandardCharsets.UTF_8);

    } catch (Exception e) {
      throw new IllegalStateException("AES 복호화 실패", e);
    }
  }
}