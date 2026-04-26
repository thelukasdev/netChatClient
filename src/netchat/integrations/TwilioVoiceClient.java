/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class TwilioVoiceClient {
    private final IntegrationConfig config;
    private final HttpJsonClient httpJsonClient;

    public TwilioVoiceClient(IntegrationConfig config, HttpJsonClient httpJsonClient) {
        this.config = config;
        this.httpJsonClient = httpJsonClient;
    }

    public boolean isEnabled() {
        return !config.getTwilioAccountSid().isBlank() && !config.getTwilioAuthToken().isBlank();
    }

    public void createVoiceEvent(String from, String to, String callbackUrl) {
        if (!isEnabled()) {
            return;
        }
        String body = "To=" + encode(to) + "&From=" + encode(from) + "&Url=" + encode(callbackUrl);
        String auth = Base64.getEncoder().encodeToString((config.getTwilioAccountSid() + ":" + config.getTwilioAuthToken()).getBytes(StandardCharsets.UTF_8));
        String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + config.getTwilioAccountSid() + "/Calls.json";
        httpJsonClient.postForm(endpoint, body, Map.of("Authorization", "Basic " + auth));
    }

    private String encode(String value) {
        return value.replace("+", "%2B").replace(" ", "%20");
    }
}
