package com.example.messagesender.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aes")
public class AESProperties {

  /**
   * 32byte (AES-256)
   */
  private String secretKey;

  /**
   * 16byte fixed IV (검색 목적)
   */
  private String fixedIv;

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getFixedIv() {
    return fixedIv;
  }

  public void setFixedIv(String fixedIv) {
    this.fixedIv = fixedIv;
  }
}
