-- =============================================
-- 贷款审批工作流引擎 - 业务表结构
-- =============================================

CREATE DATABASE IF NOT EXISTS credit_flow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE credit_flow;

-- 贷款申请表
DROP TABLE IF EXISTS `loan_application`;
CREATE TABLE `loan_application` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(64) NOT NULL COMMENT '客户姓名',
    `id_card` VARCHAR(32) NOT NULL COMMENT '身份证号',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `loan_amount` DECIMAL(15,2) NOT NULL COMMENT '申请金额(元)',
    `loan_term` INT NOT NULL COMMENT '贷款期限(月)',
    `loan_purpose` VARCHAR(128) NOT NULL COMMENT '贷款用途',
    `application_status` TINYINT NOT NULL DEFAULT 0 COMMENT '申请状态:0-待审核,1-审批中,2-通过,3-拒绝,4-撤回,5-补充资料中,6-复核中,7-已退回',
    `monthly_income` DECIMAL(15,2) NOT NULL COMMENT '月收入(元)',
    `monthly_debt` DECIMAL(15,2) NOT NULL COMMENT '月负债(元)',
    `age` INT NOT NULL COMMENT '年龄',
    `education_level` INT NOT NULL COMMENT '教育程度:1-小学及以下,2-初中,3-高中/中专,4-大专,5-本科,6-硕士及以上',
    `work_years` INT NOT NULL COMMENT '工作年限(年)',
    `has_house` TINYINT(1) DEFAULT 0 COMMENT '是否有房产',
    `has_car` TINYINT(1) DEFAULT 0 COMMENT '是否有车',
    `marital_status` VARCHAR(32) NOT NULL COMMENT '婚姻状况:SINGLE-未婚,MARRIED-已婚,DIVORCED-离异,WIDOWED-丧偶',
    `residential_address` VARCHAR(256) DEFAULT NULL COMMENT '居住地址',
    `employer` VARCHAR(128) DEFAULT NULL COMMENT '工作单位',
    `position` VARCHAR(64) DEFAULT NULL COMMENT '职位',
    `contact_name` VARCHAR(64) NOT NULL COMMENT '紧急联系人姓名',
    `contact_phone` VARCHAR(20) NOT NULL COMMENT '紧急联系人电话',
    `contact_relation` VARCHAR(32) NOT NULL COMMENT '紧急联系人关系',
    `channel` VARCHAR(64) DEFAULT NULL COMMENT '申请渠道',
    `device_id` VARCHAR(128) DEFAULT NULL COMMENT '设备ID',
    `mac_address` VARCHAR(128) DEFAULT NULL COMMENT 'MAC地址',
    `user_agent` VARCHAR(256) DEFAULT NULL COMMENT '用户代理',
    `approved_amount` DECIMAL(15,2) DEFAULT NULL COMMENT '审批额度(元)',
    `approved_term` INT DEFAULT NULL COMMENT '审批期限(月)',
    `interest_rate` DECIMAL(10,4) DEFAULT NULL COMMENT '年利率',
    `risk_level` VARCHAR(32) DEFAULT NULL COMMENT '风险等级:LOW-低,MEDIUM-中,HIGH-高',
    `credit_score` INT DEFAULT NULL COMMENT '信用评分',
    `fraud_result` TINYINT DEFAULT NULL COMMENT '反欺诈结果:0-通过,1-拒绝',
    `submit_time` DATETIME NOT NULL COMMENT '提交时间',
    `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    `reject_reason` VARCHAR(512) DEFAULT NULL COMMENT '拒绝原因',
    `return_count` INT DEFAULT 0 COMMENT '退回次数',
    `return_reason` VARCHAR(512) DEFAULT NULL COMMENT '退回原因',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_no` (`application_no`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_application_status` (`application_status`),
    KEY `idx_submit_time` (`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贷款申请表';

-- 征信查询记录表
DROP TABLE IF EXISTS `credit_query_record`;
CREATE TABLE `credit_query_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `query_type` VARCHAR(32) NOT NULL COMMENT '查询类型:PERSONAL-个人征信,BUSINESS-企业征信',
    `query_channel` VARCHAR(64) NOT NULL COMMENT '查询渠道:PBOC-人行征信,BIGDATA-大数据征信',
    `credit_score` INT DEFAULT NULL COMMENT '征信评分',
    `credit_level` VARCHAR(32) DEFAULT NULL COMMENT '征信等级',
    `overdue_count` INT DEFAULT 0 COMMENT '逾期次数',
    `overdue_amount` DECIMAL(15,2) DEFAULT 0 COMMENT '逾期金额',
    `total_loan_amount` DECIMAL(15,2) DEFAULT 0 COMMENT '总贷款金额',
    `remaining_loan_amount` DECIMAL(15,2) DEFAULT 0 COMMENT '剩余贷款金额',
    `credit_card_count` INT DEFAULT 0 COMMENT '信用卡数量',
    `credit_card_limit` DECIMAL(15,2) DEFAULT 0 COMMENT '信用卡总额度',
    `credit_card_used` DECIMAL(15,2) DEFAULT 0 COMMENT '信用卡已用额度',
    `query_result` TEXT COMMENT '查询结果(JSON)',
    `query_time` DATETIME NOT NULL COMMENT '查询时间',
    `success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功:0-否,1-是',
    `error_msg` VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_query_time` (`query_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='征信查询记录表';

-- 反欺诈结果表
DROP TABLE IF EXISTS `anti_fraud_result`;
CREATE TABLE `anti_fraud_result` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `fraud_score` INT DEFAULT NULL COMMENT '欺诈评分',
    `risk_level` VARCHAR(32) DEFAULT NULL COMMENT '风险等级:LOW-低,MEDIUM-中,HIGH-高',
    `hit_rules` TEXT COMMENT '命中规则(JSON数组)',
    `rule_count` INT DEFAULT 0 COMMENT '命中规则数量',
    `check_result` TINYINT NOT NULL DEFAULT 0 COMMENT '检查结果:0-通过,1-需人工复核,2-拒绝',
    `device_info` TEXT COMMENT '设备信息(JSON)',
    `ip_address` VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
    `geo_location` VARCHAR(128) DEFAULT NULL COMMENT '地理位置',
    `check_time` DATETIME NOT NULL COMMENT '检查时间',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_check_result` (`check_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反欺诈结果表';

-- 信用评分卡结果表
DROP TABLE IF EXISTS `credit_score_result`;
CREATE TABLE `credit_score_result` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `scorecard_version` VARCHAR(32) NOT NULL COMMENT '评分卡版本',
    `total_score` INT NOT NULL COMMENT '总评分',
    `score_level` VARCHAR(32) NOT NULL COMMENT '评分等级:A-优秀,B-良好,C-一般,D-较差,E-很差',
    `dimension_scores` TEXT COMMENT '各维度评分(JSON)',
    `pass` TINYINT NOT NULL DEFAULT 0 COMMENT '是否通过:0-否,1-是',
    `score_time` DATETIME NOT NULL COMMENT '评分时间',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_total_score` (`total_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用评分卡结果表';

-- 额度计算结果表
DROP TABLE IF EXISTS `limit_calc_result`;
CREATE TABLE `limit_calc_result` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `income_amount` DECIMAL(15,2) DEFAULT NULL COMMENT '月收入',
    `debt_ratio` DECIMAL(10,4) DEFAULT NULL COMMENT '负债比率',
    `credit_limit` DECIMAL(15,2) NOT NULL COMMENT '授信额度(元)',
    `max_available_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '最大可用额度',
    `interest_rate` DECIMAL(10,4) NOT NULL COMMENT '年利率',
    `limit_factors` TEXT COMMENT '额度计算因子(JSON)',
    `need_manual_review` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人工复核:0-否,1-是',
    `calc_time` DATETIME NOT NULL COMMENT '计算时间',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_credit_limit` (`credit_limit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度计算结果表';

-- 审批记录表
DROP TABLE IF EXISTS `approval_record`;
CREATE TABLE `approval_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) NOT NULL COMMENT '申请编号',
    `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',
    `task_id` VARCHAR(64) DEFAULT NULL COMMENT '任务ID',
    `task_key` VARCHAR(64) DEFAULT NULL COMMENT '任务定义Key',
    `task_name` VARCHAR(128) DEFAULT NULL COMMENT '任务名称',
    `approve_node` VARCHAR(64) NOT NULL COMMENT '审批节点',
    `approver` VARCHAR(64) DEFAULT NULL COMMENT '审批人',
    `approve_result` TINYINT NOT NULL COMMENT '审批结果:0-通过,1-拒绝,2-退回,3-转交',
    `approve_opinion` VARCHAR(512) DEFAULT NULL COMMENT '审批意见',
    `approve_amount` DECIMAL(15,2) DEFAULT NULL COMMENT '审批金额',
    `approve_term` INT DEFAULT NULL COMMENT '审批期限',
    `interest_rate` DECIMAL(10,4) DEFAULT NULL COMMENT '利率',
    `start_time` DATETIME DEFAULT NULL COMMENT '任务开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '任务结束时间',
    `duration` BIGINT DEFAULT NULL COMMENT '处理时长(毫秒)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_approve_node` (`approve_node`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 流程定义版本表
DROP TABLE IF EXISTS `process_definition_version`;
CREATE TABLE `process_definition_version` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `process_key` VARCHAR(64) NOT NULL COMMENT '流程Key',
    `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
    `flowable_deployment_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable部署ID',
    `flowable_definition_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable流程定义ID',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `bpmn_xml` LONGTEXT NOT NULL COMMENT 'BPMN XML内容',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0-未启用,1-已启用',
    `deploy_time` DATETIME NOT NULL COMMENT '部署时间',
    `deploy_by` VARCHAR(64) DEFAULT NULL COMMENT '部署人',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_key_version` (`process_key`, `version`),
    KEY `idx_flowable_deployment_id` (`flowable_deployment_id`),
    KEY `idx_flowable_definition_id` (`flowable_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义版本表';

-- 反欺诈规则表
DROP TABLE IF EXISTS `anti_fraud_rule`;
CREATE TABLE `anti_fraud_rule` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码',
    `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `rule_desc` VARCHAR(512) DEFAULT NULL COMMENT '规则描述',
    `rule_type` VARCHAR(32) NOT NULL COMMENT '规则类型:BLACKLIST-黑名单,THRESHOLD-阈值,DEVICE-设备,BEHAVIOR-行为',
    `rule_expression` TEXT NOT NULL COMMENT '规则表达式',
    `rule_score` INT NOT NULL DEFAULT 0 COMMENT '规则分值',
    `risk_level` VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级:LOW-低,MEDIUM-中,HIGH-高',
    `action` VARCHAR(32) NOT NULL DEFAULT 'PASS' COMMENT '触发动作:PASS-通过,ALERT-告警,REJECT-拒绝',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用:0-否,1-是',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`),
    KEY `idx_rule_type` (`rule_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反欺诈规则表';

-- 信用评分卡维度表
DROP TABLE IF EXISTS `scorecard_dimension`;
CREATE TABLE `scorecard_dimension` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
    `dimension_name` VARCHAR(128) NOT NULL COMMENT '维度名称',
    `scorecard_version` VARCHAR(32) NOT NULL COMMENT '评分卡版本',
    `weight` DECIMAL(10,4) NOT NULL COMMENT '权重',
    `max_score` INT NOT NULL COMMENT '最高分',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用:0-否,1-是',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dimension_code_version` (`dimension_code`, `scorecard_version`),
    KEY `idx_scorecard_version` (`scorecard_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用评分卡维度表';

-- 信用评分卡明细规则表
DROP TABLE IF EXISTS `scorecard_rule`;
CREATE TABLE `scorecard_rule` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `dimension_id` BIGINT NOT NULL COMMENT '维度ID',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码',
    `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `condition_expression` VARCHAR(512) NOT NULL COMMENT '条件表达式',
    `score` INT NOT NULL COMMENT '得分',
    `scorecard_version` VARCHAR(32) NOT NULL COMMENT '评分卡版本',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用:0-否,1-是',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_dimension_id` (`dimension_id`),
    KEY `idx_scorecard_version` (`scorecard_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用评分卡明细规则表';

-- =============================================
-- 初始化反欺诈规则数据
-- =============================================
INSERT INTO `anti_fraud_rule` (`id`, `rule_code`, `rule_name`, `rule_desc`, `rule_type`, `rule_expression`, `rule_score`, `risk_level`, `action`, `enabled`, `sort_order`) VALUES
(1, 'FRAUD_001', '黑名单校验', '校验客户是否在黑名单中', 'BLACKLIST', 'in(idCard, blacklist[0], blacklist[1])', 100, 'HIGH', 'REJECT', 1, 1),
(2, 'FRAUD_002', '短时间多次申请', '1小时内申请次数超过3次', 'BEHAVIOR', 'applicationCount > 3', 50, 'HIGH', 'REJECT', 1, 2),
(3, 'FRAUD_003', '异常地理位置', 'IP地址归属地与身份证地址不符', 'DEVICE', 'ipLocation != idCardLocation', 30, 'MEDIUM', 'ALERT', 1, 3),
(4, 'FRAUD_004', '设备指纹异常', '设备指纹命中风险设备库', 'DEVICE', 'in(deviceInfo, riskDevices[0], riskDevices[1])', 40, 'HIGH', 'REJECT', 1, 4),
(5, 'FRAUD_005', '贷款用途可疑', '贷款用途命中可疑关键词', 'BEHAVIOR', 'contains(loanPurpose, "投资") || contains(loanPurpose, "赌博") || contains(loanPurpose, "炒股") || contains(loanPurpose, "理财") || contains(loanPurpose, "还贷")', 20, 'MEDIUM', 'ALERT', 1, 5),
(6, 'FRAUD_006', '联系人黑名单', '紧急联系人在黑名单中', 'BLACKLIST', 'in(contactPhone, blacklist[0], blacklist[1])', 40, 'HIGH', 'REJECT', 1, 6),
(7, 'FRAUD_007', '年龄不符合要求', '申请人年龄小于18岁或大于60岁', 'THRESHOLD', 'age < 18 || age > 60', 60, 'HIGH', 'REJECT', 1, 7),
(8, 'FRAUD_008', '手机号归属地异常', '手机号归属地与常驻地不符', 'BEHAVIOR', 'phoneLocation != residentLocation', 15, 'LOW', 'ALERT', 1, 8);

-- =============================================
-- 初始化信用评分卡维度
-- =============================================
INSERT INTO `scorecard_dimension` (`id`, `dimension_code`, `dimension_name`, `scorecard_version`, `weight`, `max_score`, `enabled`, `sort_order`) VALUES
(1, 'CREDIT_HISTORY', '信用历史', 'V1.0', 0.35, 350, 1, 1),
(2, 'REPAYMENT_CAPACITY', '还款能力', 'V1.0', 0.30, 300, 1, 2),
(3, 'DEBT_RATIO', '负债比例', 'V1.0', 0.20, 200, 1, 3),
(4, 'PERSONAL_INFO', '个人信息', 'V1.0', 0.15, 150, 1, 4);

-- =============================================
-- 初始化信用评分卡规则
-- =============================================
INSERT INTO `scorecard_rule` (`id`, `dimension_id`, `dimension_code`, `rule_code`, `rule_name`, `condition_expression`, `score`, `scorecard_version`, `enabled`, `sort_order`) VALUES
-- 信用历史维度
(1, 1, 'CREDIT_HISTORY', 'CR_001', '无逾期记录', '#overdueCount == 0', 350, 'V1.0', 1, 1),
(2, 1, 'CREDIT_HISTORY', 'CR_002', '逾期1-2次', '#overdueCount >= 1 && #overdueCount <= 2', 250, 'V1.0', 1, 2),
(3, 1, 'CREDIT_HISTORY', 'CR_003', '逾期3-5次', '#overdueCount >= 3 && #overdueCount <= 5', 150, 'V1.0', 1, 3),
(4, 1, 'CREDIT_HISTORY', 'CR_004', '逾期5次以上', '#overdueCount > 5', 50, 'V1.0', 1, 4),
-- 还款能力维度
(5, 2, 'REPAYMENT_CAPACITY', 'RC_001', '月收入50000以上', '#monthlyIncome >= 50000', 300, 'V1.0', 1, 1),
(6, 2, 'REPAYMENT_CAPACITY', 'RC_002', '月收入30000-50000', '#monthlyIncome >= 30000 && #monthlyIncome < 50000', 250, 'V1.0', 1, 2),
(7, 2, 'REPAYMENT_CAPACITY', 'RC_003', '月收入15000-30000', '#monthlyIncome >= 15000 && #monthlyIncome < 30000', 200, 'V1.0', 1, 3),
(8, 2, 'REPAYMENT_CAPACITY', 'RC_004', '月收入8000-15000', '#monthlyIncome >= 8000 && #monthlyIncome < 15000', 150, 'V1.0', 1, 4),
(9, 2, 'REPAYMENT_CAPACITY', 'RC_005', '月收入8000以下', '#monthlyIncome < 8000', 80, 'V1.0', 1, 5),
-- 负债比例维度
(10, 3, 'DEBT_RATIO', 'DR_001', '负债比例30%以下', '#debtRatio < 0.3', 200, 'V1.0', 1, 1),
(11, 3, 'DEBT_RATIO', 'DR_002', '负债比例30%-50%', '#debtRatio >= 0.3 && #debtRatio < 0.5', 150, 'V1.0', 1, 2),
(12, 3, 'DEBT_RATIO', 'DR_003', '负债比例50%-70%', '#debtRatio >= 0.5 && #debtRatio < 0.7', 100, 'V1.0', 1, 3),
(13, 3, 'DEBT_RATIO', 'DR_004', '负债比例70%以上', '#debtRatio >= 0.7', 40, 'V1.0', 1, 4),
-- 个人信息维度
(14, 4, 'PERSONAL_INFO', 'PI_001', '年龄30-45岁', '#age >= 30 && #age <= 45', 150, 'V1.0', 1, 1),
(15, 4, 'PERSONAL_INFO', 'PI_002', '年龄25-30岁或45-55岁', '(#age >= 25 && #age < 30) || (#age > 45 && #age <= 55)', 120, 'V1.0', 1, 2),
(16, 4, 'PERSONAL_INFO', 'PI_003', '年龄18-25岁或55-60岁', '(#age >= 18 && #age < 25) || (#age > 55 && #age <= 60)', 80, 'V1.0', 1, 3),
(17, 4, 'PERSONAL_INFO', 'PI_004', '自有房产', '#hasHouse == true', 50, 'V1.0', 1, 4),
(18, 4, 'PERSONAL_INFO', 'PI_005', '自有车辆', '#hasCar == true', 30, 'V1.0', 1, 5),
(19, 4, 'PERSONAL_INFO', 'PI_006', '本科及以上学历', '#educationLevel >= 4', 40, 'V1.0', 1, 6),
(20, 4, 'PERSONAL_INFO', 'PI_007', '工作年限5年以上', '#workYears >= 5', 30, 'V1.0', 1, 7);

-- =============================================
-- 用户表
-- =============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `org_id` BIGINT DEFAULT NULL COMMENT '组织机构ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
    `user_type` VARCHAR(32) DEFAULT 'NORMAL' COMMENT '用户类型:ADMIN-管理员,NORMAL-普通用户,APPROVER-审批员',
    `avatar` VARCHAR(256) DEFAULT NULL COMMENT '头像',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_org_id` (`org_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 角色表
-- =============================================
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(128) NOT NULL COMMENT '角色名称',
    `role_desc` VARCHAR(512) DEFAULT NULL COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- =============================================
-- 组织机构表
-- =============================================
DROP TABLE IF EXISTS `sys_org`;
CREATE TABLE `sys_org` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `org_code` VARCHAR(64) NOT NULL COMMENT '机构编码',
    `org_name` VARCHAR(128) NOT NULL COMMENT '机构名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父机构ID',
    `org_level` INT DEFAULT 1 COMMENT '机构层级',
    `org_type` VARCHAR(32) DEFAULT 'DEPARTMENT' COMMENT '机构类型:COMPANY-公司,DEPARTMENT-部门,TEAM-小组',
    `manager` VARCHAR(64) DEFAULT NULL COMMENT '负责人',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    `address` VARCHAR(256) DEFAULT NULL COMMENT '地址',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_org_code` (`org_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织机构表';

-- =============================================
-- 用户角色关联表
-- =============================================
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- =============================================
-- 初始化组织机构数据
-- =============================================
INSERT INTO `sys_org` (`id`, `org_code`, `org_name`, `parent_id`, `org_level`, `org_type`, `manager`, `phone`, `sort_order`) VALUES
(1, 'BC_CREDIT', '北京银行信贷中心', 0, 1, 'COMPANY', '张总', '010-88888888', 1),
(2, 'RISK_DEPT', '风险管理部', 1, 2, 'DEPARTMENT', '李经理', '010-88888801', 1),
(3, 'CREDIT_DEPT', '信贷审批部', 1, 2, 'DEPARTMENT', '王经理', '010-88888802', 2),
(4, 'OPERATION_DEPT', '运营管理部', 1, 2, 'DEPARTMENT', '赵经理', '010-88888803', 3),
(5, 'FIRST_APPROVAL', '初审组', 3, 3, 'TEAM', '孙组长', '010-88888811', 1),
(6, 'FINAL_APPROVAL', '终审组', 3, 3, 'TEAM', '周组长', '010-88888812', 2);

-- =============================================
-- 初始化角色数据
-- =============================================
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_desc`, `sort_order`) VALUES
(1, 'ADMIN', '系统管理员', '拥有系统所有权限', 1),
(2, 'CUSTOMER', '客户', '贷款申请人', 2),
(3, 'FIRST_APPROVER', '初审员', '一级审批人员', 3),
(4, 'FINAL_APPROVER', '终审员', '终审审批人员', 4),
(5, 'RISK_OFFICER', '风控官', '风险管理人员', 5);

-- =============================================
-- 初始化用户数据 (密码: 123456, 通过BCrypt加密)
-- =============================================
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `org_id`, `status`, `user_type`, `remark`) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'admin@bc.com', '13800138000', 1, 1, 'ADMIN', '系统默认管理员'),
(2, 'customer001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张三', 'zhangsan@example.com', '13800138001', 1, 1, 'NORMAL', '测试客户'),
(3, 'approver01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李初审', 'lifirst@bc.com', '13800138002', 5, 1, 'APPROVER', '初审员'),
(4, 'approver02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王终审', 'wangfinal@bc.com', '13800138003', 6, 1, 'APPROVER', '终审员');

-- =============================================
-- 初始化用户角色关联数据
-- =============================================
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 4);

-- =============================================
-- 征信API调用日志表
-- =============================================
DROP TABLE IF EXISTS `credit_api_call_log`;
CREATE TABLE `credit_api_call_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `query_id` VARCHAR(64) NOT NULL COMMENT '查询批次ID',
    `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求ID',
    `application_id` BIGINT DEFAULT NULL COMMENT '申请ID',
    `application_no` VARCHAR(64) DEFAULT NULL COMMENT '申请编号',
    `customer_id` VARCHAR(64) NOT NULL COMMENT '客户ID',
    `data_source` VARCHAR(32) NOT NULL COMMENT '数据源类型:PBOC-央行,BAIHANG-百行,SOCIAL_SECURITY-社保,HOUSING_FUND-公积金',
    `data_source_name` VARCHAR(64) NOT NULL COMMENT '数据源名称',
    `query_mode` VARCHAR(16) NOT NULL COMMENT '查询模式:SYNC-同步,ASYNC-异步',
    `request_body` TEXT COMMENT '请求报文(JSON)',
    `response_body` TEXT COMMENT '响应报文(JSON)',
    `cost_ms` BIGINT DEFAULT NULL COMMENT '调用耗时(毫秒)',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功:0-否,1-是',
    `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
    `error_msg` VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    `quality_tag` VARCHAR(32) DEFAULT NULL COMMENT '数据质量标签:NORMAL-正常,FALLBACK-降级,PENDING_REVIEW-待复核,PARTIAL-部分失败',
    `circuit_breaker_status` VARCHAR(32) DEFAULT NULL COMMENT '熔断器状态:CLOSED-关闭,OPEN-打开,HALF_OPEN-半开',
    `call_time` DATETIME NOT NULL COMMENT '调用时间',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0-未删除,1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_query_id` (`query_id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_data_source` (`data_source`),
    KEY `idx_call_time` (`call_time`),
    KEY `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='征信API调用日志表';
