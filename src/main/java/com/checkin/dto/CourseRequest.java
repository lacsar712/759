package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建课程请求
 */
@Data
public class CourseRequest {

    @NotBlank(message = "课程名称不能为空")
    private String courseName;
}
