package com.bc.credit.integration.adapter;

import com.bc.credit.common.enums.CreditDataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CreditDataAdapterFactory {

    private final Map<CreditDataSourceType, CreditDataAdapter> adapterMap = new ConcurrentHashMap<>();

    @Autowired
    public CreditDataAdapterFactory(List<CreditDataAdapter> adapters) {
        for (CreditDataAdapter adapter : adapters) {
            adapterMap.put(adapter.getDataSourceType(), adapter);
            log.info("注册征信数据源适配器: {} - {}", adapter.getDataSourceType().getCode(), adapter.getDataSourceName());
        }
    }

    public CreditDataAdapter getAdapter(CreditDataSourceType type) {
        CreditDataAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("未找到数据源适配器: " + type.getCode());
        }
        return adapter;
    }

    public boolean hasAdapter(CreditDataSourceType type) {
        return adapterMap.containsKey(type);
    }

    public java.util.Collection<CreditDataAdapter> getAllAdapters() {
        return adapterMap.values();
    }
}
