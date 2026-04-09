package com.ayssu.ciphergate.thirdparty.auth;

public final class ThirdPartyHeaders {
    private ThirdPartyHeaders() {
    }

    public static final String X_APP_KEY = "X-App-Key";
    public static final String X_TIMESTAMP = "X-Timestamp";
    public static final String X_NONCE = "X-Nonce";
    public static final String X_SIGNATURE = "X-Signature";

    public static final String X_RESP_TIMESTAMP = "X-Resp-Timestamp";
    public static final String X_RESP_NONCE = "X-Resp-Nonce";
    public static final String X_RESP_SIGNATURE = "X-Resp-Signature";

    public static final String ATTR_APPLICATION_ID = "thirdParty.applicationId";
    public static final String ATTR_APP_KEY = "thirdParty.appKey";
    public static final String ATTR_ENCRYPTION_PLUGIN_ID = "thirdParty.encryptionPluginId";
    public static final String ATTR_APP_SECRET = "thirdParty.appSecret";
    public static final String ATTR_ENCRYPTION_CONFIG = "thirdParty.encryptionConfig";
}

