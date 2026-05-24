package com.template.dto;

import lombok.Data;

/**
 * 差异对比请求 DTO
 */
@Data
public class ParseDiffCompareRequest {
    private Long recordIdA;
    private Long recordIdB;
}
