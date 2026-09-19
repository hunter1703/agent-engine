package com.agentengine.util.crypto;

import com.agentengine.util.common.Cache;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.InfraConfigService;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.agentengine.util.crypto.CryptoClient.PREFIX;
import static com.agentengine.util.crypto.CryptoClient.SEPARATOR;

// ApplicationScoped, not Singleton: the Mongo client needs this service for its secure-field codec,
// and this service reads its keys through InfraConfigService, which reads Mongo. CDI injects a
// client proxy for a normal scope and builds the real instance on the first call, which breaks
// that construction cycle.
@ApplicationScoped
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
    final int keyIdEnd = ciphertext.indexOf(SEPARATOR, PREFIX.length());
    if (keyIdEnd < 0) {
      throw new IllegalArgumentException("Ciphertext has no key id");
    }
    final String keyId = ciphertext.substring(PREFIX.length(), keyIdEnd);
    return clientProvider.getForKeyId(keyId).decrypt(ciphertext);
  }

  private CryptoClient getClient() {
    return Context.customerId().map(clientProvider::get).orElse(null);
  }
}
