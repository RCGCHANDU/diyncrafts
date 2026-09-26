package com.diyncrafts.web.app.model;

/**
 * Transcoding task lifecycle. Persisted by ordinal: append new values at the end only.
 */
public enum TaskStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED
}
