/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.shared.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

public class Packet {
    private final MessageType type;
    private final Map<String, String> values;

    private Packet(MessageType type, Map<String, String> values) {
        this.type = type;
        this.values = values;
    }

    public static Packet of(MessageType type) {
        return new Packet(type, new LinkedHashMap<>());
    }

    public Packet with(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.put(key, value == null ? "" : value);
        return new Packet(type, copy);
    }

    public MessageType type() {
        return type;
    }

    public Map<String, String> values() {
        return Map.copyOf(values);
    }

    public String getOrDefault(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }
}

