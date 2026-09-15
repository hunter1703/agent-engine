package com.agentengine.util.common;

public interface EncryptionService {

  boolean isEncryptionEnabled();

  boolean isEncrypted(String text);

  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
