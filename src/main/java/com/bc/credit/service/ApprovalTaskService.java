package com.bc.credit.service;

import com.bc.credit.entity.LoanApplication;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ApprovalTaskService {

    List<Map<String, Object>> getTodoTasks(String assignee, List<String> candidateGroups, int page, int size);

    Map<String, Object> getTaskDetail(String taskId);

    void completeManualReview(String taskId, String assignee, String result, String opinion,
                              BigDecimal approveAmount, Integer approveTerm, BigDecimal interestRate);

    void completeFinalApproval(String taskId, String assignee, String result, String opinion,
                               BigDecimal approveAmount, Integer approveTerm, BigDecimal interestRate);

    void completeTask(String taskId, String assignee, Map<String, Object> variables);

    void claimTask(String taskId, String assignee);

    void unclaimTask(String taskId);

    void delegateTask(String taskId, String assignee, String toUser);

    List<Map<String, Object>> getApprovalHistory(String applicationNo);
}
