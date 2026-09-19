package com.agentengine.util.cloudstorage;

import com.agentengine.util.context.Context;
import com.agentengine.util.infra.InfraConfigService;

public abstract class AbstractCloudStorageService implements CloudStorageService {
    private static final String  DEFAULT_BUCKET = "agentengine";

    protected final InfraConfigService infraConfigService;

    protected AbstractCloudStorageService(InfraConfigService infraConfigService) {
        this.infraConfigService = infraConfigService;
    }


    protected String bucket() {
        return Context.customerId().map(customerId -> {
            final CloudStorageClientInfraConfig clientConfig = infraConfigService.get(CloudStorageUtils.clientId(customerId));
            return clientConfig == null ? null : clientConfig.getBucket();
        }).orElse(DEFAULT_BUCKET);
    }
}
