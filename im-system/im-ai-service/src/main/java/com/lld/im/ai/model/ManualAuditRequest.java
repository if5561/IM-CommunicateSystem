package com.lld.im.ai.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualAuditRequest {

    @NotBlank
    private String text;
}
