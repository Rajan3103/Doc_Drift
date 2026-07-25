package com.docdrift.Docdrift.dto;

import lombok.Data;

@Data
public class LlmSuggestionDto {
    private String oldText;
    private String suggestedText;
    private String reason;
}
