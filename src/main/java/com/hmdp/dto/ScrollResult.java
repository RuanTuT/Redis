package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @description: TODO  滚动分页结果类
 * @date 2023/5/13 18:34
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
