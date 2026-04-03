package com.agentengine.util.common.exception;

public class AssetNotFoundException extends RuntimeException {
    private final String assetType;
    private final String assetId;

    public AssetNotFoundException(final String assetType, final String assetId) {
        super(buildMessage(assetType, assetId));
        this.assetType = assetType;
        this.assetId = assetId;
    }

    public AssetNotFoundException(final String assetType, final String assetId, final Throwable cause) {
        super(buildMessage(assetType, assetId), cause);
        this.assetType = assetType;
        this.assetId = assetId;
    }

    public String getAssetType() {
        return assetType;
    }

    public String getAssetId() {
        return assetId;
    }

    private static String buildMessage(final String assetType, final String assetId) {
        return "Asset not found: type=" + String.valueOf(assetType) + ", identifier=" + String.valueOf(assetId);
    }
}
