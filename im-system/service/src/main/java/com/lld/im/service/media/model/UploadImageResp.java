package com.lld.im.service.media.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadImageResp {

    private String imageUrl;

    private String storagePath;

    private String fileName;

    private Integer width;

    private Integer height;

    private Long size;

    private String contentType;

    private String messageBody;

    private ImageMessageBody imageMessageBody;
}
