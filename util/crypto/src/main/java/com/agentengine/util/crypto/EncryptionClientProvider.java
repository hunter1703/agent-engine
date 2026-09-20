package com.agentengine.util.crypto;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.DefaultServers;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import jakarta.inject.Singleton;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Singleton
public class EncryptionClientProvider extends InfraClientFactory<EncryptionClientInfraConfig, EncryptionKeyInfraConfig, CryptoClient> {

    private final DefaultServers defaultServers;

    protected EncryptionClientProvider(
            InfraConfigService infraConfigService,
            DistributedCacheManager cacheManager,
            DefaultServers defaultServers) {
        super(infraConfigService, cacheManager, EncryptionKeyInfraConfig.TYPE);
        this.defaultServers = defaultServers;
    }

    public CryptoClient get(final int customerId) {
        return get(
                getOrCreate(
                        EncryptionUtils.clientId(customerId),
                        () -> EncryptionUtils.clientConfig(
                                customerId, defaultServers.serverId(DefaultServers.ENCRYPTION))));
    }

    public CryptoClient getForKeyId(final String keyId) {
        return getClientForServer(infraConfigService.get(EncryptionKeyInfraConfig.TYPE + ":" + keyId));
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

        final byte[] decoded = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decoded, "AES");
    }

    private static String decodeWithVault(final EncryptionKeyInfraConfig config) {
        try (KmsCryptoClient client =
                     KmsCryptoClient.builder()
                             .endpoint(config.getCryptoEndpoint())
                             .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build())) {
            return client
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
        }
    }
}
