package com.agentengine.util;

public interface EncryptionService {

  boolean isEncryptionEnabled();

  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
