package com.lld.im.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lld.im.ai.model.ImageInsightResult;
import com.lld.im.ai.model.ImageMessageBody;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class ImageInsightService {

    private static final String SYSTEM_PROMPT = """
            你是 IM 图片消息的解析助手。
            任务：
            1. 提取图片中清晰可见的文字，放到 ocrText 字段。
            2. 用一句简洁中文总结图片内容，放到 caption 字段。
            3. 只返回严格 JSON：{"ocrText":"...","caption":"..."}。
            4. 如果图片里没有文字，ocrText 返回空字符串。
            """;

    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    public ImageInsightService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public ImageInsightResult analyze(ImageMessageBody imageMessageBody) {
        String fallbackCaption = buildFallbackCaption(imageMessageBody);
        if (!StringUtils.hasText(imageMessageBody.getStoragePath())) {
            return new ImageInsightResult(safeText(imageMessageBody.getOcrText()),
                    mergeCaption(imageMessageBody.getCaption(), fallbackCaption));
        }

        try {
            Path imagePath = Path.of(imageMessageBody.getStoragePath());
            if (!Files.exists(imagePath)) {
                return new ImageInsightResult(safeText(imageMessageBody.getOcrText()),
                        mergeCaption(imageMessageBody.getCaption(), fallbackCaption));
            }

            MimeType mimeType = detectMimeType(imagePath, imageMessageBody.getContentType());
            Media media = new Media(mimeType, new FileSystemResource(imagePath));
            String response = chatModel.call(
                    new SystemMessage(SYSTEM_PROMPT),
                    UserMessage.builder()
                            .text("请分析这张图片，输出 OCR 文本和一句中文描述。")
                            .media(List.of(media))
                            .metadata(Map.of())
                            .build()
            );
            JsonNode root = objectMapper.readTree(response);
            String ocrText = safeText(root.path("ocrText").asText());
            String caption = mergeCaption(root.path("caption").asText(), fallbackCaption);
            return new ImageInsightResult(ocrText, caption);
        }
        catch (Exception ignored) {
            return new ImageInsightResult(safeText(imageMessageBody.getOcrText()),
                    mergeCaption(imageMessageBody.getCaption(), fallbackCaption));
        }
    }

    private MimeType detectMimeType(Path imagePath, String contentType) {
        if (StringUtils.hasText(contentType)) {
            return MimeTypeUtils.parseMimeType(contentType);
        }
        try {
            String detected = Files.probeContentType(imagePath);
            if (StringUtils.hasText(detected)) {
                return MimeTypeUtils.parseMimeType(detected);
            }
        }
        catch (Exception ignored) {
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private String buildFallbackCaption(ImageMessageBody imageMessageBody) {
        if (StringUtils.hasText(imageMessageBody.getCaption())) {
            return imageMessageBody.getCaption().trim();
        }
        if (StringUtils.hasText(imageMessageBody.getFileName())) {
            return "图片消息：" + imageMessageBody.getFileName().trim();
        }
        return "图片消息";
    }

    private String mergeCaption(String caption, String fallbackCaption) {
        if (StringUtils.hasText(caption)) {
            return caption.trim();
        }
        return fallbackCaption;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
