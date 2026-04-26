/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.transmission;

import java.time.LocalDateTime;

public class DeliveryRecord {
    private final String transferId;
    private final DeliveryChannel channel;
    private final String sourceNode;
    private final String targetNode;
    private final String payloadType;
    private final String payloadSummary;
    private final LocalDateTime createdAt;
    private DeliveryStatus status;

    public DeliveryRecord(String transferId, DeliveryChannel channel, String sourceNode, String targetNode,
                          String payloadType, String payloadSummary, LocalDateTime createdAt, DeliveryStatus status) {
        this.transferId = transferId;
        this.channel = channel;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.payloadType = payloadType;
        this.payloadSummary = payloadSummary;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getTransferId() {
        return transferId;
    }

    public DeliveryChannel getChannel() {
        return channel;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public String getPayloadSummary() {
        return payloadSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }
}

