package com.template.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustRequestDTO {
    @NotNull(message = "adjustJson 不能为空")
    @JsonProperty("manualAdjustJson")
    private String adjustJson;
}
