package com.propfolio.documents;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a bill should be read (after upload, or "Re-run extraction").
 * The extraction package listens and starts the reading once the transaction commits.
 *
 * @param requestedAt identifies this request; a reading for an older request is discarded
 * @param automatic   true for the automatic reading after upload
 */
public record BillExtractionRequested(UUID billId, Instant requestedAt, boolean automatic) {
}
