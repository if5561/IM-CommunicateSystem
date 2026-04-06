package com.lld.im.service.media.model;

import lombok.Data;

@Data
public class ImageMessageBody {

    private String type = "image";

    private String imageUrl;

    private String storagePath;

    private String fileName;

    private Integer width;

    private Integer height;

    private Long size;

    private String contentType;

    private String ocrText = "";

    private String caption = "";
}
