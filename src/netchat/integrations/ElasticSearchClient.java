/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.util.Map;

public class ElasticSearchClient {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public ElasticSearchClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getElasticEndpoint().isBlank() && !config.getElasticApiKey().isBlank();
    }

    public void index(String index, String id, String jsonDocument) {
        if (!isEnabled()) {
            return;
        }
        String endpoint = config.getElasticEndpoint() + "/" + index + "/_doc/" + id;
        httpJsonClient.postJson(endpoint, jsonDocument, Map.of(
                "Authorization", "ApiKey " + config.getElasticApiKey()
        ));
    }
}
