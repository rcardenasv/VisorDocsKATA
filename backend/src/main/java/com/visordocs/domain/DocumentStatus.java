package com.visordocs.domain;

/**
 * Possible states of a document during its lifecycle.
 *
 * <pre>
 * PROCESSING → INDEXED   (success)
 * PROCESSING → ERROR     (failure)
 * </pre>
 */
public enum DocumentStatus {
    PROCESSING,
    INDEXED,
    ERROR
}
