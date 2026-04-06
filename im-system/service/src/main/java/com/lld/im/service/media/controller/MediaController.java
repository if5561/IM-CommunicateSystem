package com.lld.im.service.media.controller;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.media.model.UploadImageResp;
import com.lld.im.service.media.service.LocalImageStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("v1/media")
public class MediaController {

    private final LocalImageStorageService localImageStorageService;

    public MediaController(LocalImageStorageService localImageStorageService) {
        this.localImageStorageService = localImageStorageService;
    }

    @PostMapping(value = "/image/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseVO<UploadImageResp> uploadImage(@RequestPart("file") MultipartFile file) throws IOException {
        return ResponseVO.successResponse(localImageStorageService.store(file));
    }
}
