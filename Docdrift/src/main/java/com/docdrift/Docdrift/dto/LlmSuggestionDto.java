package com.docdrift.Docdrift.dto;

import lombok.Data;

@Data
public class LlmSuggestionDto {
    private String filePath;
    private String driftType;
    private String severity;
    private Double confidenceScore;
    private String impactedSymbol;
    private String oldText;
    private String suggestedText;
    private String reason;
}
