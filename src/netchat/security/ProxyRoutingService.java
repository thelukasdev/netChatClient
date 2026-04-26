/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProxyRoutingService {
    private final List<ProxyNode> proxyNodes = new ArrayList<>();

    public ProxyRoutingService() {
        proxyNodes.add(new ProxyNode("edge-eu-1", "10.10.0.11", 9001, 1));
        proxyNodes.add(new ProxyNode("edge-eu-2", "10.10.0.12", 9002, 2));
        proxyNodes.add(new ProxyNode("edge-us-1", "10.20.0.21", 9003, 3));
    }

    public ProxyNode selectProxy() {
        return proxyNodes.stream()
                .filter(ProxyNode::isOnline)
                .min(Comparator.comparingInt(ProxyNode::getPriority))
                .orElse(proxyNodes.getFirst());
    }

    public String describeTopology() {
        StringBuilder builder = new StringBuilder("Proxy topology:\n");
        for (ProxyNode node : proxyNodes) {
            builder.append("- ")
                    .append(node.getNodeId())
                    .append(" -> ")
                    .append(node.getHost())
                    .append(":")
                    .append(node.getPort())
                    .append(" priority=")
                    .append(node.getPriority())
                    .append(" online=")
                    .append(node.isOnline())
                    .append("\n");
        }
        return builder.toString().trim();
    }
}

