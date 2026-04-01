package com.agentengine.util.common;

public interface EncryptionService {

    boolean isEncryptionEnabled();

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
