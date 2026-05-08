package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.Min;

/**
 * 发起签到活动请求
 */
@Data
public class OpenSessionRequest {

    @Min(value = 1, message = "持续时间必须大于0")
    private Integer durationMinutes = 10; // 默认10分钟
}
