package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class DataPointDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String date;

    private Object value;
}
