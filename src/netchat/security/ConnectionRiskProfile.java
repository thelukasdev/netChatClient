/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.security;

public class ConnectionRiskProfile {
    private final String ipAddress;
    private final boolean proxyDetected;
    private final boolean vpnDetected;
    private final int riskScore;
    private final String reason;

    public ConnectionRiskProfile(String ipAddress, boolean proxyDetected, boolean vpnDetected, int riskScore, String reason) {
        this.ipAddress = ipAddress;
        this.proxyDetected = proxyDetected;
        this.vpnDetected = vpnDetected;
        this.riskScore = riskScore;
        this.reason = reason;
    }

    public String serialize() {
        return ipAddress + ";" + proxyDetected + ";" + vpnDetected + ";" + riskScore + ";" + reason;
    }

    public boolean isHighRisk() {
        return riskScore >= 75;
    }

    public String getReason() {
        return reason;
    }

    public int getRiskScore() {
        return riskScore;
    }
}

