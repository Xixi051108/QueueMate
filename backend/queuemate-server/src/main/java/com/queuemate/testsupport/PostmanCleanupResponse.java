package com.queuemate.testsupport;

public record PostmanCleanupResponse(
        String runId,
        int usersDeleted,
        int venuesDeleted,
        int slotsDeleted,
        int bookingsDeleted,
        int vouchersDeleted,
        int queueTicketsDeleted,
        int walletTransactionsDeleted,
        int adjustmentsDeleted,
        int remainingArtifacts
) {
}
