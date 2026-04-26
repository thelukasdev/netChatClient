/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.util.Map;

public class FirebasePushClient {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public FirebasePushClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getFirebaseProjectId().isBlank() && !config.getFirebaseBearerToken().isBlank();
    }

    public void sendTopicNotification(String topic, String title, String body) {
        if (!isEnabled()) {
            return;
        }
        String endpoint = "https://fcm.googleapis.com/v1/projects/" + config.getFirebaseProjectId() + "/messages:send";
        String json = "{"
                + "\"message\":{"
                + "\"topic\":\"" + escape(topic) + "\","
                + "\"notification\":{\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body) + "\"}"
                + "}}";
        httpJsonClient.postJson(endpoint, json, Map.of(
                "Authorization", "Bearer " + config.getFirebaseBearerToken()
        ));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
