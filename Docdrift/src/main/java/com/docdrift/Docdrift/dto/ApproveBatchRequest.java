package com.docdrift.Docdrift.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApproveBatchRequest(@NotEmpty @Size(max = 50) List<Long> ids) {
}
