/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import netchat.server.ChatServer;
import netchat.server.RealtimeCallSession;
import netchat.server.ServerState;
import netchat.shared.model.ChatRoom;
import netchat.shared.model.Message;
import netchat.shared.model.User;
import netchat.transmission.DeliveryRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebAdminServer {
    private final int port;
    private final ChatServer chatServer;
    private final ServerState state;
    private final WebAppAssetRepository assets;
    private HttpServer httpServer;

    public WebAdminServer(int port, ChatServer chatServer, ServerState state) {
        this.port = port;
        this.chatServer = chatServer;
        this.state = state;
        this.assets = new WebAppAssetRepository(Path.of("webapp"));
    }

    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.createContext("/", exchange -> serveAsset(exchange, "index.html", "text/html; charset=UTF-8"));
            httpServer.createContext("/app.css", exchange -> serveAsset(exchange, "app.css", "text/css; charset=UTF-8"));
            httpServer.createContext("/app.js", exchange -> serveAsset(exchange, "app.js", "application/javascript; charset=UTF-8"));
            httpServer.createContext("/api/dashboard", exchange -> writeJson(exchange, buildDashboardJson()));
            httpServer.createContext("/api/admin/action", new AdminActionHandler());
            httpServer.start();
            System.out.println("Web admin app running at http://127.0.0.1:" + port);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start web admin server on port " + port, exception);
        }
    }

    private void serveAsset(HttpExchange exchange, String relativePath, String contentType) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] response = assets.loadText(relativePath).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private String buildDashboardJson() {
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"overview\":").append(buildOverviewJson()).append(",");
        builder.append("\"users\":").append(buildUsersJson()).append(",");
        builder.append("\"rooms\":").append(buildRoomsJson()).append(",");
        builder.append("\"calls\":").append(buildCallsJson()).append(",");
        builder.append("\"transfers\":").append(buildTransfersJson()).append(",");
        builder.append("\"audits\":").append(buildAuditsJson()).append(",");
        builder.append("\"storage\":").append(JsonBuilder.quote(state.getPersistenceService().describeStorageLayout())).append(",");
        builder.append("\"proxyTopology\":").append(JsonBuilder.quote(state.getProxyRoutingService().describeTopology())).append(",");
        builder.append("\"integrations\":").append(JsonBuilder.quote(state.getExternalPlatformHub().describeEnabledIntegrations()));
        builder.append("}");
        return builder.toString();
    }

    private String buildOverviewJson() {
        return "{"
                + "\"userCount\":" + state.getUsers().size() + ","
                + "\"onlineCount\":" + state.getUsers().values().stream().filter(User::isOnline).count() + ","
                + "\"roomCount\":" + state.getRooms().size() + ","
                + "\"activeCalls\":" + state.getCallSessions().size() + ","
                + "\"recentTransfers\":" + state.getTransmissionService().getRecentTransfers().size()
                + "}";
    }

    private String buildUsersJson() {
        List<User> users = new ArrayList<>(state.getUsers().values());
        users.sort(Comparator.comparing(User::getUsername));
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < users.size(); index++) {
            User user = users.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"username\":").append(JsonBuilder.quote(user.getUsername())).append(",")
                    .append("\"displayName\":").append(JsonBuilder.quote(user.getDisplayName())).append(",")
                    .append("\"role\":").append(JsonBuilder.quote(user.getRole().name())).append(",")
                    .append("\"online\":").append(user.isOnline()).append(",")
                    .append("\"banned\":").append(user.isBanned()).append(",")
                    .append("\"muted\":").append(user.isMuted()).append(",")
                    .append("\"muteSeconds\":").append(user.remainingMuteSeconds())
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildRoomsJson() {
        List<ChatRoom> rooms = new ArrayList<>(state.getRooms().values());
        rooms.sort(Comparator.comparing(ChatRoom::getId));
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < rooms.size(); index++) {
            ChatRoom room = rooms.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"id\":").append(JsonBuilder.quote(room.getId())).append(",")
                    .append("\"displayName\":").append(JsonBuilder.quote(room.getDisplayName())).append(",")
                    .append("\"description\":").append(JsonBuilder.quote(room.getDescription())).append(",")
                    .append("\"members\":").append(room.getMembers().size()).append(",")
                    .append("\"messages\":").append(buildMessagesJson(room.getHistory()))
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildMessagesJson(List<Message> messages) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"sender\":").append(JsonBuilder.quote(message.getSender())).append(",")
                    .append("\"content\":").append(JsonBuilder.quote(message.getContent())).append(",")
                    .append("\"createdAt\":").append(JsonBuilder.quote(message.getCreatedAt().toString()))
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildCallsJson() {
        List<RealtimeCallSession> calls = new ArrayList<>(state.getCallSessions().values());
        calls.sort(Comparator.comparing(RealtimeCallSession::getCallId));
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < calls.size(); index++) {
            RealtimeCallSession call = calls.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"callId\":").append(JsonBuilder.quote(call.getCallId())).append(",")
                    .append("\"caller\":").append(JsonBuilder.quote(call.getCallerUsername())).append(",")
                    .append("\"callee\":").append(JsonBuilder.quote(call.getCalleeUsername())).append(",")
                    .append("\"mode\":").append(JsonBuilder.quote(call.getMediaMode().name())).append(",")
                    .append("\"accepted\":").append(call.isAccepted())
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildAuditsJson() {
        List<String> audits = new ArrayList<>(state.getAuditTrail());
        audits.sort(Comparator.reverseOrder());
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < audits.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(JsonBuilder.quote(audits.get(index)));
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildTransfersJson() {
        List<DeliveryRecord> transfers = new ArrayList<>(state.getTransmissionService().getRecentTransfers());
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < transfers.size(); index++) {
            DeliveryRecord transfer = transfers.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"transferId\":").append(JsonBuilder.quote(transfer.getTransferId())).append(",")
                    .append("\"channel\":").append(JsonBuilder.quote(transfer.getChannel().name())).append(",")
                    .append("\"source\":").append(JsonBuilder.quote(transfer.getSourceNode())).append(",")
                    .append("\"target\":").append(JsonBuilder.quote(transfer.getTargetNode())).append(",")
                    .append("\"payloadType\":").append(JsonBuilder.quote(transfer.getPayloadType())).append(",")
                    .append("\"payloadSummary\":").append(JsonBuilder.quote(transfer.getPayloadSummary())).append(",")
                    .append("\"status\":").append(JsonBuilder.quote(transfer.getStatus().name())).append(",")
                    .append("\"createdAt\":").append(JsonBuilder.quote(transfer.getCreatedAt().toString()))
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private final class AdminActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", "*");
                headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
                headers.set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> payload = parseForm(exchange.getRequestBody().readAllBytes());
            String action = payload.getOrDefault("action", "");
            String username = payload.getOrDefault("username", "");
            String result = switch (action) {
                case "kick" -> chatServer.kickUserBySystem(username);
                case "ban" -> chatServer.banUserBySystem(username);
                case "mute" -> chatServer.muteUserBySystem(username, parseLong(payload.getOrDefault("seconds", "60")));
                case "announce" -> chatServer.broadcastAnnouncement(payload.getOrDefault("message", ""));
                case "snapshot" -> chatServer.createSystemSnapshot();
                default -> "Unknown action.";
            };
            writeJson(exchange, "{\"result\":" + JsonBuilder.quote(result) + "}");
        }

        private Map<String, String> parseForm(byte[] requestBytes) {
            String body = new String(requestBytes, StandardCharsets.UTF_8);
            Map<String, String> values = new LinkedHashMap<>();
            for (String pair : body.split("&")) {
                int separator = pair.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                values.put(key, value);
            }
            return values;
        }

        private long parseLong(String rawValue) {
            try {
                return Long.parseLong(rawValue);
            } catch (NumberFormatException exception) {
                return 60L;
            }
        }
    }
}

