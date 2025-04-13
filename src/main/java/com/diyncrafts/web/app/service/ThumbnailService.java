package com.diyncrafts.web.app.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;

@Service
public class ThumbnailService {
    // Extract a thumbnail frame from a video byte array
    public BufferedImage extractThumbnail(byte[] videoBytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(videoBytes);
             FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream)) {
            grabber.start();
            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("Failed to extract thumbnail");
            }
            Java2DFrameConverter converter = new Java2DFrameConverter();
            return converter.getBufferedImage(frame);
        }
    }
}