package com.template.dto;

import lombok.Data;

@Data
public class TrainTaskProgressDTO {

    private Integer progress;

    private Integer currentFile;

    private Integer totalFiles;

    private String message;
}
