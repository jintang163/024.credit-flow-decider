package com.bc.credit.dto.credit;

import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.common.enums.QueryMode;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class CreditQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String customerName;

    private String idCard;

    private String phone;

    private Long applicationId;

    private String applicationNo;

    private List<CreditDataSourceType> dataSources;

    private QueryMode queryMode = QueryMode.SYNC;

    private String callbackUrl;

    private String requestId;
}
