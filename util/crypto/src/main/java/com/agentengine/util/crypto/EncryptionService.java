package com.agentengine.util.crypto;

public interface EncryptionService {

  boolean isEncryptionEnabled();

  boolean isEncrypted(String text);

  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
