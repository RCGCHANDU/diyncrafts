package com.diyncrafts.web.app.transcoding;

/**
 * A permanent processing failure (bad input, ffmpeg error, timeout). Retrying will not help, so
 * the task is marked FAILED immediately. {@link #getMessage()} is safe to show to the uploader;
 * technical detail belongs in the cause and the server log.
 */
public class TranscodingException extends RuntimeException {

    public TranscodingException(String userMessage) {
        super(userMessage);
    }

    public TranscodingException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
