package com.bc.credit.service;

public interface IdempotentService {

    boolean checkAndAcquire(String key, long expireSeconds);

    void release(String key);

    String getExistingResponse(String key);

    void saveResponse(String key, String response, long expireSeconds);

    boolean isProcessing(String key);
}
