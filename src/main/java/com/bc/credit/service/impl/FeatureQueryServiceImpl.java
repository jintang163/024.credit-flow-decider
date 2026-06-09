package com.bc.credit.service.impl;

import com.bc.credit.mapper.FraudBlacklistMapper;
import com.bc.credit.mapper.FraudDeviceFingerprintMapper;
import com.bc.credit.mapper.FraudMultiHeadLendingMapper;
import com.bc.credit.mapper.FraudRiskIpPoolMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.FeatureQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FeatureQueryServiceImpl implements FeatureQueryService {

    private static final String REDIS_BLACKLIST_PREFIX = "anti-fraud:blacklist:";
    private static final String REDIS_IP_RISK_PREFIX = "anti-fraud:ip-risk:";
    private static final String REDIS_DEVICE_ASSOC_PREFIX = "anti-fraud:device-assoc:";
    private static final String REDIS_MULTI_HEAD_PREFIX = "anti-fraud:multi-head:";
    private static final String REDIS_IP_LOCATION_PREFIX = "anti-fraud:ip-location:";
    private static final long CACHE_TTL_HOURS = 1;

    @Autowired
    private FraudBlacklistMapper fraudBlacklistMapper;

    @Autowired
    private FraudRiskIpPoolMapper fraudRiskIpPoolMapper;

    @Autowired
    private FraudDeviceFingerprintMapper fraudDeviceFingerprintMapper;

    @Autowired
    private FraudMultiHeadLendingMapper fraudMultiHeadLendingMapper;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${credit.anti-fraud.feature.external-api.enabled:false}")
    private boolean externalApiEnabled;

    @Value("${credit.anti-fraud.feature.external-api.base-url:}")
    private String externalApiBaseUrl;

    @Value("${credit.anti-fraud.feature.external-api.api-key:}")
    private String externalApiKey;

    @Value("${credit.anti-fraud.feature.external-api.timeout-ms:3000}")
    private int externalApiTimeoutMs;

    private WebClient webClient;

    @javax.annotation.PostConstruct
    public void init() {
        if (externalApiBaseUrl != null && !externalApiBaseUrl.isEmpty()) {
            this.webClient = WebClient.builder()
                    .baseUrl(externalApiBaseUrl)
                    .defaultHeader("X-API-Key", externalApiKey)
                    .build();
        }
    }

    @Override
    public int getDeviceFingerprintAssocCount(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return 0;
        }

        String cacheKey = REDIS_DEVICE_ASSOC_PREFIX + deviceId;
        String cached = getFromRedis(cacheKey);
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        int count = fraudDeviceFingerprintMapper.countAssocIdCardsIn1Hour(deviceId);

        if (externalApiEnabled && count == 0) {
            count = queryExternalDeviceAssoc(deviceId);
        }

        setToRedis(cacheKey, String.valueOf(count), CACHE_TTL_HOURS);

        log.debug("Device fingerprint assoc count, deviceId: {}, count: {}", deviceId, count);
        return count;
    }

    @Override
    public boolean isIpInRiskProxyPool(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }

        String cacheKey = REDIS_IP_RISK_PREFIX + ipAddress;
        String cached = getFromRedis(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }

        boolean inPool = false;

        if (stringRedisTemplate != null) {
            Set<String> members = stringRedisTemplate.opsForSet()
                    .members("anti-fraud:risk-ip-set");
            if (members != null && members.contains(ipAddress)) {
                inPool = true;
            }
        }

        if (!inPool) {
            int dbCount = fraudRiskIpPoolMapper.countByIpOrSegment(ipAddress);
            inPool = dbCount > 0;
        }

        if (!inPool && externalApiEnabled) {
            inPool = queryExternalIpRisk(ipAddress);
        }

        setToRedis(cacheKey, inPool ? "1" : "0", CACHE_TTL_HOURS);

        log.debug("IP risk proxy check, ipAddress: {}, inPool: {}", ipAddress, inPool);
        return inPool;
    }

    @Override
    public boolean isContactInBlacklist(String contactPhone) {
        return isTargetInBlacklist("PHONE", contactPhone);
    }

    @Override
    public boolean isIdCardInBlacklist(String idCard) {
        return isTargetInBlacklist("IDCARD", idCard);
    }

    private boolean isTargetInBlacklist(String targetType, String targetValue) {
        if (targetValue == null || targetValue.isEmpty()) {
            return false;
        }

        String cacheKey = REDIS_BLACKLIST_PREFIX + targetType + ":" + targetValue;
        String cached = getFromRedis(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }

        boolean inBlacklist = false;

        if (stringRedisTemplate != null) {
            Boolean isMember = stringRedisTemplate.opsForSet()
                    .isMember("anti-fraud:blacklist:" + targetType, targetValue);
            inBlacklist = Boolean.TRUE.equals(isMember);
        }

        if (!inBlacklist) {
            int dbCount = fraudBlacklistMapper.countByTarget(targetType, targetValue);
            inBlacklist = dbCount > 0;
        }

        if (!inBlacklist && externalApiEnabled) {
            inBlacklist = queryExternalBlacklist(targetType, targetValue);
        }

        setToRedis(cacheKey, inBlacklist ? "1" : "0", CACHE_TTL_HOURS);

        log.debug("Blacklist check, type: {}, value: {}, inBlacklist: {}", targetType, targetValue, inBlacklist);
        return inBlacklist;
    }

    @Override
    public int getMultiHeadLendingCount7d(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return 0;
        }

        String cacheKey = REDIS_MULTI_HEAD_PREFIX + "7d:" + idCard;
        String cached = getFromRedis(cacheKey);
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        int count = fraudMultiHeadLendingMapper.countInstitutionsByIdCard(idCard, 7);

        if (externalApiEnabled && count == 0) {
            count = queryExternalMultiHeadLending(idCard, 7);
        }

        setToRedis(cacheKey, String.valueOf(count), CACHE_TTL_HOURS);

        log.debug("Multi-head lending count 7d, idCard: {}, count: {}", idCard, count);
        return count;
    }

    @Override
    public List<String> getBlacklistByType(String targetType) {
        List<String> result = new ArrayList<>();

        if (stringRedisTemplate != null) {
            Set<String> members = stringRedisTemplate.opsForSet()
                    .members("anti-fraud:blacklist:" + targetType);
            if (members != null) {
                result.addAll(members);
            }
        }

        if (result.isEmpty()) {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.bc.credit.entity.FraudBlacklist> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("target_type", targetType)
                    .eq("deleted", 0)
                    .isNull("expire_time").or().gt("expire_time", LocalDateTime.now())
                    .select("target_value");
            List<com.bc.credit.entity.FraudBlacklist> records = fraudBlacklistMapper.selectList(wrapper);
            for (com.bc.credit.entity.FraudBlacklist record : records) {
                result.add(record.getTargetValue());
            }
        }

        return result;
    }

    @Override
    public String getIpLocation(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "";
        }

        String cacheKey = REDIS_IP_LOCATION_PREFIX + ipAddress;
        String cached = getFromRedis(cacheKey);
        if (cached != null) {
            return cached;
        }

        String location = "";

        if (externalApiEnabled && webClient != null) {
            try {
                location = webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/ip-location")
                                .queryParam("ip", ipAddress)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(java.time.Duration.ofMillis(externalApiTimeoutMs))
                        .block();
                if (location == null) {
                    location = "";
                }
            } catch (Exception e) {
                log.warn("External IP location API failed, ip: {}", ipAddress, e);
                location = "";
            }
        }

        if (location.isEmpty()) {
            location = queryLocalIpLocation(ipAddress);
        }

        setToRedis(cacheKey, location, 24);

        return location;
    }

    @Override
    public String getIdCardProvince(String idCard) {
        if (idCard == null || idCard.length() < 6) {
            return "";
        }

        String prefix = idCard.substring(0, 2);

        if (stringRedisTemplate != null) {
            String province = (String) stringRedisTemplate.opsForHash()
                    .get("anti-fraud:province-code-map", prefix);
            if (province != null) {
                return province;
            }
        }

        java.util.Map<String, String> provinceCodes = buildProvinceCodeMap();
        return provinceCodes.getOrDefault(prefix, "");
    }

    @Override
    public int getRecentApplicationCount(String customerId, int days) {
        if (customerId == null || customerId.isEmpty()) {
            return 0;
        }

        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.bc.credit.entity.LoanApplication> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("customer_id", customerId)
                    .ge("submit_time", LocalDateTime.now().minusDays(days));
            return Math.toIntExact(loanApplicationMapper.selectCount(wrapper));
        } catch (Exception e) {
            log.warn("Failed to get recent application count, customerId: {}", customerId, e);
            return 0;
        }
    }

    private int queryExternalDeviceAssoc(String deviceId) {
        if (webClient == null) {
            return 0;
        }
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/device-fingerprint/assoc-count")
                            .queryParam("deviceId", deviceId)
                            .queryParam("windowHours", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(externalApiTimeoutMs))
                    .block();
            return result != null ? Integer.parseInt(result) : 0;
        } catch (Exception e) {
            log.warn("External device fingerprint API failed, deviceId: {}", deviceId, e);
            return 0;
        }
    }

    private boolean queryExternalIpRisk(String ipAddress) {
        if (webClient == null) {
            return false;
        }
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/ip-risk/check")
                            .queryParam("ip", ipAddress)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(externalApiTimeoutMs))
                    .block();
            return "true".equalsIgnoreCase(result);
        } catch (Exception e) {
            log.warn("External IP risk API failed, ip: {}", ipAddress, e);
            return false;
        }
    }

    private boolean queryExternalBlacklist(String targetType, String targetValue) {
        if (webClient == null) {
            return false;
        }
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/blacklist/check")
                            .queryParam("type", targetType)
                            .queryParam("value", targetValue)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(externalApiTimeoutMs))
                    .block();
            return "true".equalsIgnoreCase(result);
        } catch (Exception e) {
            log.warn("External blacklist API failed, type: {}, value: {}", targetType, targetValue, e);
            return false;
        }
    }

    private int queryExternalMultiHeadLending(String idCard, int days) {
        if (webClient == null) {
            return 0;
        }
        try {
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/multi-head-lending/count")
                            .queryParam("idCard", idCard)
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(externalApiTimeoutMs))
                    .block();
            return result != null ? Integer.parseInt(result) : 0;
        } catch (Exception e) {
            log.warn("External multi-head lending API failed, idCard: {}", idCard, e);
            return 0;
        }
    }

    private String queryLocalIpLocation(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            try {
                long ipLong = (Long.parseLong(parts[0]) << 24)
                        | (Long.parseLong(parts[1]) << 16)
                        | (Long.parseLong(parts[2]) << 8)
                        | Long.parseLong(parts[3]);

                if (ipLong >= ipToLong("10.0.0.0") && ipLong <= ipToLong("10.255.255.255")) {
                    return "内网地址";
                }
                if (ipLong >= ipToLong("172.16.0.0") && ipLong <= ipToLong("172.31.255.255")) {
                    return "内网地址";
                }
                if (ipLong >= ipToLong("192.168.0.0") && ipLong <= ipToLong("192.168.255.255")) {
                    return "内网地址";
                }
                if (ipLong >= ipToLong("127.0.0.0") && ipLong <= ipToLong("127.255.255.255")) {
                    return "本地回环";
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return "未知";
    }

    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        return (Long.parseLong(parts[0]) << 24)
                | (Long.parseLong(parts[1]) << 16)
                | (Long.parseLong(parts[2]) << 8)
                | Long.parseLong(parts[3]);
    }

    private String getFromRedis(String key) {
        try {
            if (stringRedisTemplate != null) {
                return stringRedisTemplate.opsForValue().get(key);
            }
        } catch (Exception e) {
            log.warn("Redis read failed, key: {}", key, e);
        }
        return null;
    }

    private void setToRedis(String key, String value, long ttlHours) {
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue().set(key, value, Duration.ofHours(ttlHours));
            }
        } catch (Exception e) {
            log.warn("Redis write failed, key: {}", key, e);
        }
    }

    private java.util.Map<String, String> buildProvinceCodeMap() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("11", "北京市");
        map.put("12", "天津市");
        map.put("13", "河北省");
        map.put("14", "山西省");
        map.put("15", "内蒙古自治区");
        map.put("21", "辽宁省");
        map.put("22", "吉林省");
        map.put("23", "黑龙江省");
        map.put("31", "上海市");
        map.put("32", "江苏省");
        map.put("33", "浙江省");
        map.put("34", "安徽省");
        map.put("35", "福建省");
        map.put("36", "江西省");
        map.put("37", "山东省");
        map.put("41", "河南省");
        map.put("42", "湖北省");
        map.put("43", "湖南省");
        map.put("44", "广东省");
        map.put("45", "广西壮族自治区");
        map.put("46", "海南省");
        map.put("50", "重庆市");
        map.put("51", "四川省");
        map.put("52", "贵州省");
        map.put("53", "云南省");
        map.put("54", "西藏自治区");
        map.put("61", "陕西省");
        map.put("62", "甘肃省");
        map.put("63", "青海省");
        map.put("64", "宁夏回族自治区");
        map.put("65", "新疆维吾尔自治区");
        map.put("71", "台湾省");
        map.put("81", "香港特别行政区");
        map.put("82", "澳门特别行政区");
        return map;
    }
}
