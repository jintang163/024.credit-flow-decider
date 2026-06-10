package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ProcessInstanceQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String applicationNo;

    private String idCard;

    private String status;

    private String processKey;

    private Integer page = 1;

    private Integer size = 10;
}
