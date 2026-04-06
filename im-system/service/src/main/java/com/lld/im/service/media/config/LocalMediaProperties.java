package com.lld.im.service.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "im.media")
public class LocalMediaProperties {

    private String uploadDir = "./uploads/images";

    private String accessUrlPrefix = "/media/images";

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getAccessUrlPrefix() {
        return accessUrlPrefix;
    }

    public void setAccessUrlPrefix(String accessUrlPrefix) {
        this.accessUrlPrefix = normalizePrefix(accessUrlPrefix);
    }

    public Path getUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String getResourceLocation() {
        return getUploadRoot().toUri().toString();
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "/media/images";
        }
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
