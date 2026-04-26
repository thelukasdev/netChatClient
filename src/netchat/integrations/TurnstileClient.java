/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TurnstileClient {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public TurnstileClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getTurnstileSecret().isBlank();
    }

    public boolean verify(String token, String remoteIp) {
        if (!isEnabled() || token == null || token.isBlank()) {
            return true;
        }
        String form = "secret=" + encode(config.getTurnstileSecret())
                + "&response=" + encode(token)
                + "&remoteip=" + encode(remoteIp == null ? "" : remoteIp);
        String response = httpJsonClient.postForm("https://challenges.cloudflare.com/turnstile/v0/siteverify", form, Map.of());
        return response.contains("\"success\":true");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
