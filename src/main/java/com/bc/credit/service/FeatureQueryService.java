package com.bc.credit.service;

import java.util.List;

public interface FeatureQueryService {

    int getDeviceFingerprintAssocCount(String deviceId);

    boolean isIpInRiskProxyPool(String ipAddress);

    boolean isContactInBlacklist(String contactPhone);

    boolean isIdCardInBlacklist(String idCard);

    int getMultiHeadLendingCount7d(String idCard);

    List<String> getBlacklistByType(String targetType);

    String getIpLocation(String ipAddress);

    String getIdCardProvince(String idCard);

    int getRecentApplicationCount(String customerId, int days);
}
