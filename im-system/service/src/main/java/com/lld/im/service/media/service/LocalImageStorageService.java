package com.lld.im.service.media.service;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.service.media.config.LocalMediaProperties;
import com.lld.im.service.media.model.ImageMessageBody;
import com.lld.im.service.media.model.UploadImageResp;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalImageStorageService {

    private static final Set<String> ALLOWED_SUFFIX = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LocalMediaProperties localMediaProperties;

    public LocalImageStorageService(LocalMediaProperties localMediaProperties) {
        this.localMediaProperties = localMediaProperties;
    }

    public UploadImageResp store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = extractSuffix(originalFilename);
        if (!ALLOWED_SUFFIX.contains(suffix.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、gif、bmp、webp 图片上传");
        }

        Path dayDirectory = localMediaProperties.getUploadRoot().resolve(LocalDate.now().format(DATE_FORMATTER));
        Files.createDirectories(dayDirectory);

        String storedFileName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        Path targetFile = dayDirectory.resolve(storedFileName).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        BufferedImage bufferedImage = ImageIO.read(targetFile.toFile());
        if (bufferedImage == null) {
            Files.deleteIfExists(targetFile);
            throw new IllegalArgumentException("上传文件不是有效图片");
        }

        String relativePath = dayDirectory.getFileName() + "/" + storedFileName;
        String imageUrl = localMediaProperties.getAccessUrlPrefix() + "/" + relativePath.replace("\\", "/");

        ImageMessageBody imageMessageBody = new ImageMessageBody();
        imageMessageBody.setImageUrl(imageUrl);
        imageMessageBody.setStoragePath(targetFile.toString());
        imageMessageBody.setFileName(StringUtils.hasText(originalFilename) ? originalFilename : storedFileName);
        imageMessageBody.setWidth(bufferedImage.getWidth());
        imageMessageBody.setHeight(bufferedImage.getHeight());
        imageMessageBody.setSize(file.getSize());
        imageMessageBody.setContentType(file.getContentType());

        return UploadImageResp.builder()
                .imageUrl(imageUrl)
                .storagePath(targetFile.toString())
                .fileName(imageMessageBody.getFileName())
                .width(imageMessageBody.getWidth())
                .height(imageMessageBody.getHeight())
                .size(imageMessageBody.getSize())
                .contentType(imageMessageBody.getContentType())
                .messageBody(JSONObject.toJSONString(imageMessageBody))
                .imageMessageBody(imageMessageBody)
                .build();
    }

    private String extractSuffix(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("图片文件缺少后缀名");
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }
}
