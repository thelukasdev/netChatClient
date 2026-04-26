/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

public class ExternalPlatformHub {
    private final IntegrationConfig config;
    private final PostgresArchiveService postgresArchiveService;
    private final RedisTelemetryService redisTelemetryService;
    private final ObjectStorageClient objectStorageClient;
    private final TurnstileClient turnstileClient;
    private final OpenAiModerationClient openAiModerationClient;
    private final FirebasePushClient firebasePushClient;
    private final TwilioVoiceClient twilioVoiceClient;
    private final ElasticSearchClient elasticSearchClient;

    public ExternalPlatformHub() {
        this.config = new IntegrationConfig();
        HttpJsonClient httpJsonClient = new HttpJsonClient();
        this.postgresArchiveService = new PostgresArchiveService(config);
        this.redisTelemetryService = new RedisTelemetryService(config);
        this.objectStorageClient = new ObjectStorageClient(config, httpJsonClient);
        this.turnstileClient = new TurnstileClient(config, httpJsonClient);
        this.openAiModerationClient = new OpenAiModerationClient(config, httpJsonClient);
        this.firebasePushClient = new FirebasePushClient(config, httpJsonClient);
        this.twilioVoiceClient = new TwilioVoiceClient(config, httpJsonClient);
        this.elasticSearchClient = new ElasticSearchClient(config, httpJsonClient);
        this.postgresArchiveService.ensureSchema();
    }

    public CloudArchivePublisher getCloudArchivePublisher() {
        return objectStorageClient;
    }

    public boolean verifyHuman(String token, String remoteIp) {
        return turnstileClient.verify(token, remoteIp);
    }

    public boolean shouldBlockContent(String content) {
        return openAiModerationClient.isFlagged(content);
    }

    public void onUserPresence(String username, boolean online) {
        redisTelemetryService.publishPresence(username, online);
        postgresArchiveService.archive("presence", username, online ? "online" : "offline");
    }

    public void onArchiveRecord(String category, String referenceId, String payload) {
        postgresArchiveService.archive(category, referenceId, payload);
    }

    public void onAuditIndexed(String auditId, String json) {
        elasticSearchClient.index("netchat-audit", auditId, json);
    }

    public void onMessageIndexed(String messageId, String json) {
        elasticSearchClient.index("netchat-messages", messageId, json);
    }

    public void pushAnnouncement(String title, String body) {
        firebasePushClient.sendTopicNotification("netchat-global", title, body);
    }

    public void onExternalVoiceBridge(String from, String to, String callbackUrl) {
        twilioVoiceClient.createVoiceEvent(from, to, callbackUrl);
    }

    public String describeEnabledIntegrations() {
        return "Integrations: postgres=" + postgresArchiveService.isEnabled()
                + ", redis=" + redisTelemetryService.isEnabled()
                + ", objectStorage=" + objectStorageClient.isEnabled()
                + ", turnstile=" + turnstileClient.isEnabled()
                + ", openAiModeration=" + openAiModerationClient.isEnabled()
                + ", firebase=" + firebasePushClient.isEnabled()
                + ", twilio=" + twilioVoiceClient.isEnabled()
                + ", elastic=" + elasticSearchClient.isEnabled();
    }
}
