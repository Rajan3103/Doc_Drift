package com.docdrift.Docdrift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EditSuggestionRequest(@NotNull @Size(max = 50000) String suggestedText) {
}
