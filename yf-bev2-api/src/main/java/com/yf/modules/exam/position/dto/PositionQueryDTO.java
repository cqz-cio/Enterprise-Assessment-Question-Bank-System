package com.yf.modules.exam.position.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PositionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String departmentId;
    private Integer status;
}
