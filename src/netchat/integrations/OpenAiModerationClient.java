/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.util.Map;

public class OpenAiModerationClient {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public OpenAiModerationClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getOpenAiApiKey().isBlank();
    }

    public boolean isFlagged(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return false;
        }
        String json = "{"
                + "\"model\":\"omni-moderation-latest\","
                + "\"input\":" + quote(text)
                + "}";
        String response = httpJsonClient.postJson("https://api.openai.com/v1/moderations", json, Map.of(
                "Authorization", "Bearer " + config.getOpenAiApiKey()
        ));
        return response.contains("\"flagged\":true");
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
