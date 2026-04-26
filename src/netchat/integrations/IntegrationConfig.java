/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

public class IntegrationConfig {
    private final String postgresUrl = read("netchat.postgres.url", "");
    private final String postgresUser = read("netchat.postgres.user", "");
    private final String postgresPassword = read("netchat.postgres.password", "");
    private final String redisUrl = read("netchat.redis.url", "");
    private final String minioEndpoint = read("netchat.minio.endpoint", "");
    private final String minioAccessKey = read("netchat.minio.accessKey", "");
    private final String minioSecretKey = read("netchat.minio.secretKey", "");
    private final String minioBucket = read("netchat.minio.bucket", "netchat-archive");
    private final String turnstileSecret = read("netchat.turnstile.secret", "");
    private final String openAiApiKey = read("netchat.openai.apiKey", "");
    private final String firebaseProjectId = read("netchat.firebase.projectId", "");
    private final String firebaseBearerToken = read("netchat.firebase.bearerToken", "");
    private final String twilioAccountSid = read("netchat.twilio.accountSid", "");
    private final String twilioAuthToken = read("netchat.twilio.authToken", "");
    private final String elasticEndpoint = read("netchat.elastic.endpoint", "");
    private final String elasticApiKey = read("netchat.elastic.apiKey", "");

    public String getPostgresUrl() {
        return postgresUrl;
    }

    public String getPostgresUser() {
        return postgresUser;
    }

    public String getPostgresPassword() {
        return postgresPassword;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public String getMinioEndpoint() {
        return minioEndpoint;
    }

    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    public String getMinioBucket() {
        return minioBucket;
    }

    public String getTurnstileSecret() {
        return turnstileSecret;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public String getFirebaseProjectId() {
        return firebaseProjectId;
    }

    public String getFirebaseBearerToken() {
        return firebaseBearerToken;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public String getTwilioAuthToken() {
        return twilioAuthToken;
    }

    public String getElasticEndpoint() {
        return elasticEndpoint;
    }

    public String getElasticApiKey() {
        return elasticApiKey;
    }

    private String read(String key, String fallback) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        return envValue == null || envValue.isBlank() ? fallback : envValue;
    }
}
