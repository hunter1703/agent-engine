package com.agentengine.util.crypto;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Singleton
public class EncryptionClientProvider
    extends InfraClientFactory<
        EncryptionClientInfraConfig, EncryptionKeyInfraConfig, CryptoClient> {

  private final ApplicationConfig applicationConfig;

  protected EncryptionClientProvider(
      InfraConfigService infraConfigService,
      DistributedCacheManager cacheManager,
      ApplicationConfig applicationConfig) {
    super(infraConfigService, cacheManager, ServerType.ENCRYPTION_KEY);
    this.applicationConfig = applicationConfig;
  }

  public CryptoClient get(final int customerId) {
    return get(
        getOrCreate(
            EncryptionUtils.clientId(customerId),
            () ->
                EncryptionUtils.clientConfig(
                    customerId, ServerType.ENCRYPTION_KEY.defaultServerId(applicationConfig))));
  }

  public CryptoClient getForKeyId(final String keyId) {
    return getClientForServer(infraConfigService.get(ServerType.ENCRYPTION_KEY + ":" + keyId));
  }

  @Override
  protected CryptoClient create(EncryptionKeyInfraConfig serverConfig) {
    return new CryptoClient(buildKey(serverConfig), serverConfig.getKeyId());
  }

  private static SecretKey buildKey(final EncryptionKeyInfraConfig config) {
    final String base64Key =
        switch (config.providerType()) {
          case KEY -> config.getKey();
          case OCI_KMS -> decodeWithVault(config);
          case UNKNOWN ->
              throw new IllegalStateException(
                  "Unsupported encryption provider '"
                      + config.getProvider()
                      + "' in key "
                      + config.getKeyId());
        };

    if (StringUtils.isBlank(base64Key)) {
      throw new IllegalStateException("Empty or missing encryption key for " + config.getKeyId());
    }

    final byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
    return new SecretKeySpec(decoded, "AES");
  }

  private static String decodeWithVault(final EncryptionKeyInfraConfig config) {
    try (KmsCryptoClient client =
        KmsCryptoClient.builder()
            .endpoint(config.getCryptoEndpoint())
            .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build())) {
      final String kmsPlaintext =
          client
              .decrypt(
                  DecryptRequest.builder()
                      .decryptDataDetails(
                          DecryptDataDetails.builder()
                              .keyId(config.getVaultKeyId())
                              .ciphertext(config.getKey())
                              .build())
                      .build())
              .getDecryptedData()
              .getPlaintext();
      return new String(Base64.getDecoder().decode(kmsPlaintext), StandardCharsets.UTF_8).trim();
    }
  }
}
