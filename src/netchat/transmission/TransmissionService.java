/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.transmission;

import netchat.persistence.PersistenceService;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class TransmissionService {
    private final Deque<DeliveryRecord> recentTransfers = new ArrayDeque<>();
    private final PersistenceService persistenceService;

    public TransmissionService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public synchronized DeliveryRecord queue(DeliveryChannel channel, String sourceNode, String targetNode, String payloadType, String payloadSummary) {
        DeliveryRecord record = new DeliveryRecord(
                UUID.randomUUID().toString(),
                channel,
                sourceNode,
                targetNode,
                payloadType,
                payloadSummary,
                LocalDateTime.now(),
                DeliveryStatus.QUEUED
        );
        recentTransfers.addFirst(record);
        while (recentTransfers.size() > 200) {
            recentTransfers.removeLast();
        }
        persistenceService.appendTransferRecord(record);
        persistenceService.archiveCloudObject(record, "queued");
        return record;
    }

    public synchronized void markDelivered(DeliveryRecord record) {
        record.setStatus(DeliveryStatus.DELIVERED);
        persistenceService.appendTransferRecord(record);
        persistenceService.archiveCloudObject(record, "delivered");
    }

    public synchronized void markFailed(DeliveryRecord record, String reason) {
        record.setStatus(DeliveryStatus.FAILED);
        persistenceService.appendTransferRecord(record);
        persistenceService.archiveCloudObject(record, "failed:" + reason);
    }

    public synchronized void archiveReplicaSync(String sourceNode, String targetNode, String payloadSummary) {
        DeliveryRecord record = queue(DeliveryChannel.SERVER_TO_SERVER, sourceNode, targetNode, "replica-sync", payloadSummary);
        markDelivered(record);
    }

    public synchronized Deque<DeliveryRecord> getRecentTransfers() {
        return new ArrayDeque<>(recentTransfers);
    }
}

