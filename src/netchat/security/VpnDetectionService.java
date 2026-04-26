/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.security;

import java.util.Set;

public class VpnDetectionService {
    private final Set<String> suspiciousPrefixes = Set.of("45.", "46.", "51.", "89.", "91.", "185.");
    private final Set<String> suspiciousHostMarkers = Set.of("vpn", "proxy", "tunnel", "hosting", "colo");

    public ConnectionRiskProfile analyze(String ipAddress, String reverseHost) {
        boolean proxyDetected = reverseHost != null && suspiciousHostMarkers.stream().anyMatch(marker -> reverseHost.toLowerCase().contains(marker));
        boolean vpnDetected = suspiciousPrefixes.stream().anyMatch(ipAddress::startsWith);
        int riskScore = 0;
        StringBuilder reason = new StringBuilder();

        if (proxyDetected) {
            riskScore += 45;
            reason.append("reverse host suggests a proxy service; ");
        }
        if (vpnDetected) {
            riskScore += 40;
            reason.append("IP range matches configured VPN heuristics; ");
        }
        if (ipAddress.startsWith("127.") || ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.")) {
            reason.append("private or loopback network detected; ");
        }
        if (riskScore == 0) {
            reason.append("no major tunnel indicators found");
        }

        return new ConnectionRiskProfile(ipAddress, proxyDetected, vpnDetected, riskScore, reason.toString().trim());
    }
}

