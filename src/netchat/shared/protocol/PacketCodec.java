/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.protocol;

import netchat.shared.security.CryptoUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PacketCodec {
    private final String secret;

    public PacketCodec(String secret) {
        this.secret = secret;
    }

    public String encode(Packet packet) {
        StringBuilder builder = new StringBuilder(packet.type().name());
        for (var entry : packet.values().entrySet()) {
            builder.append("|")
                    .append(entry.getKey())
                    .append("=")
                    .append(Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return CryptoUtils.encrypt(builder.toString(), secret);
    }

    public Packet decode(String encryptedPayload) {
        String plainText = CryptoUtils.decrypt(encryptedPayload, secret);
        String[] parts = plainText.split("\\|", -1);
        Packet packet = Packet.of(MessageType.valueOf(parts[0]));

        for (int index = 1; index < parts.length; index++) {
            int separator = parts[index].indexOf('=');
            if (separator < 0) {
                continue;
            }

            String key = parts[index].substring(0, separator);
            String base64 = parts[index].substring(separator + 1);
            String value = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            packet = packet.with(key, value);
        }
        return packet;
    }
}

