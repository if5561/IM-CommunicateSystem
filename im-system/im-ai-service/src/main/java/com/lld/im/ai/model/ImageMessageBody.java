package com.lld.im.ai.model;

import lombok.Data;

@Data
public class ImageMessageBody {

    private String type;

    private String imageUrl;

    private String storagePath;

    private String fileName;

    private Integer width;

    private Integer height;

    private Long size;

    private String contentType;

    private String ocrText;

    private String caption;
}
