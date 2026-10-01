package com.agentengine.util.crypto;

import static com.agentengine.util.crypto.CryptoClient.PREFIX;
import static com.agentengine.util.crypto.CryptoClient.SEPARATOR;

import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Context;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EncryptionServiceImpl implements EncryptionService {
  private static final Logger LOG = LoggerFactory.getLogger(EncryptionServiceImpl.class);
  private final EncryptionClientProvider clientProvider;

  public EncryptionServiceImpl(final EncryptionClientProvider clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public boolean isEncrypted(final String text) {
    return StringUtils.isNotBlank(text) && text.startsWith(PREFIX);
  }

  @Override
  public boolean isEncryptionEnabled() {
    return getClient() != null;
  }

  @Override
  public String encrypt(final String plaintext) {
    if (!isEncryptionEnabled()) {
      return plaintext;
    }
    return getClient().encrypt(plaintext);
  }

  @Override
  public String decrypt(final String ciphertext) {
    if (!isEncrypted(ciphertext)) {
      return ciphertext;
    }
    final int keyIdEnd = ciphertext.indexOf(SEPARATOR, PREFIX.length());
    if (keyIdEnd < 0) {
      throw new IllegalArgumentException("Ciphertext has no key id");
    }
    final String keyId = ciphertext.substring(PREFIX.length(), keyIdEnd);
    return clientProvider.getForKeyId(keyId).decrypt(ciphertext);
  }

  private CryptoClient getClient() {
    return Context.currentCustomerId().map(clientProvider::get).orElse(null);
  }
}
