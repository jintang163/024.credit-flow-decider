package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AntiFraudRuleFact implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String idCard;
    private String phone;
    private String customerName;
    private BigDecimal loanAmount;
    private Integer loanTerm;
    private String loanPurpose;
    private String applicationNo;
    private String ipAddress;
    private String deviceInfo;
    private String deviceId;

    private int deviceFingerprintAssocCount;
    private boolean ipInRiskProxyPool;
    private boolean contactInBlacklist;
    private int multiHeadLendingCount7d;
    private BigDecimal debtRatio;
    private int recentApplicationCount;
    private int age;
    private String idCardLocation;
    private String phoneLocation;
    private String ipLocation;
    private String residentLocation;
    private List<String> blacklist;
    private List<String> riskDevices;
    private List<String> riskIpPool;
    private String contactPhone;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyDebt;

    private boolean hit;
    private String hitRuleCode;
    private String hitRuleName;
    private int hitScore;
    private String hitRiskLevel;
    private String hitAction;
    private String hitDetail;
}
