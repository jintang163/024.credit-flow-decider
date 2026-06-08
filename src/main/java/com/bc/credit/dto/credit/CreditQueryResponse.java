package com.bc.credit.dto.credit;

import com.bc.credit.common.enums.DataQualityTag;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CreditQueryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;

    private String queryId;

    private boolean success;

    private String message;

    private StructuredCreditData data;

    private DataQualityTag qualityTag;

    private Map<String, DataSourceResult> dataSourceResults;

    private Long totalCostMs;

    private LocalDateTime queryTime;

    private boolean asyncQuery;

    @Data
    public static class DataSourceResult implements Serializable {
        private String dataSourceCode;
        private String dataSourceName;
        private boolean success;
        private Long costMs;
        private String errorMsg;
        private Integer retryCount;
        private String rawResponse;
    }

    public boolean isAllSuccess() {
        if (dataSourceResults == null || dataSourceResults.isEmpty()) {
            return success;
        }
        return dataSourceResults.values().stream().allMatch(DataSourceResult::isSuccess);
    }

    public List<String> getFailedDataSources() {
        if (dataSourceResults == null || dataSourceResults.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dataSourceResults.entrySet().stream()
                .filter(e -> !e.getValue().isSuccess())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }
}
