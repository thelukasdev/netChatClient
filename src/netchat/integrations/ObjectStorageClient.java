/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.util.Base64;
import java.util.Map;

public class ObjectStorageClient implements CloudArchivePublisher {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public ObjectStorageClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getMinioEndpoint().isBlank();
    }

    @Override
    public void publish(String objectName, String encryptedPayload) {
        if (!isEnabled()) {
            return;
        }
        String json = "{"
                + "\"bucket\":\"" + escape(config.getMinioBucket()) + "\","
                + "\"object\":\"" + escape(objectName) + "\","
                + "\"payload\":\"" + escape(Base64.getEncoder().encodeToString(encryptedPayload.getBytes())) + "\""
                + "}";
        httpJsonClient.postJson(config.getMinioEndpoint(), json, Map.of(
                "X-Access-Key", config.getMinioAccessKey(),
                "X-Secret-Key", config.getMinioSecretKey()
        ));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
