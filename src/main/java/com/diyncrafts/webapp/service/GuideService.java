package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Guide;
import com.diyncrafts.webapp.repository.jpa.GuideRepository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class GuideService {

    @Autowired
    private GuideRepository guideRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public Guide createGuide(Guide guide, MultipartFile imageFile) throws IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build(), RequestBody.fromBytes(imageFile.getBytes()));
            guide.setImageUrl("https://" + bucketName + ".s3.amazonaws.com/" + fileName);
        }
        return guideRepository.save(guide);
    }

    public List<Guide> getGuidesByVideoId(String videoId, int page, int size) {
        int offset = (page - 1) * size;
        return guideRepository.findByVideoId(videoId, offset, size);
    }

    public Guide updateGuide(Long id, Guide updatedGuide, MultipartFile imageFile) throws IOException {
        Guide existingGuide = guideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guide not found"));

        existingGuide.setTitle(updatedGuide.getTitle());
        existingGuide.setContent(updatedGuide.getContent());

        if (imageFile != null && !imageFile.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build(), RequestBody.fromBytes(imageFile.getBytes()));
            existingGuide.setImageUrl("https://" + bucketName + ".s3.amazonaws.com/" + fileName);
        }

        return guideRepository.save(existingGuide);
    }

    public void deleteGuide(Long id) {
        guideRepository.deleteById(id);
    }
}