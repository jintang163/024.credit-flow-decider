# 贷款审批工作流引擎 (Credit Flow Decider)

基于 Spring Boot + Flowable 的贷款审批工作流引擎，集成征信数据、反欺诈规则和信用评分卡，自动输出授信额度。

## 功能特性

### 流程定义与版本管理
- 基于 BPMN 2.0 设计审批流程，包含申请提交、征信查询、反欺诈校验、信用评分、额度计算、人工复核、终审等节点
- 支持顺序、并行、条件分支（按金额或风险等级分流）
- 提供流程部署与版本化 API，支持热加载新版本，存量实例沿用旧版本
- 运行时通过流程引擎启动实例，跟踪当前节点与流程变量
- 支持挂起、恢复、终止流程实例
- 提供流程模板的导入/导出功能，便于多环境迁移

### 核心业务模块
- **征信数据集成**：模拟外部征信接口，获取客户征信信息
- **反欺诈规则引擎**：基于规则库的欺诈检测，支持黑名单、阈值、设备、行为等多种规则类型
- **信用评分卡**：多维度评分模型，包括信用历史、还款能力、负债比例、个人信息等维度
- **额度计算服务**：基于评分、风险等级、收入负债比等因素计算授信额度和利率

## 技术栈

- **后端框架**：Spring Boot 2.7.18
- **流程引擎**：Flowable 6.8.0
- **ORM 框架**：MyBatis-Plus 3.5.3.1
- **数据库**：MySQL 8.0+
- **连接池**：Druid 1.2.16
- **接口文档**：Knife4j 4.3.0
- **工具库**：Hutool 5.8.20, Fastjson2 2.0.32

## 项目结构

```
credit-flow-decider/
├── src/main/java/com/bc/credit/
│   ├── common/              # 通用模块
│   │   ├── Result.java       # 统一响应结果
│   │   └── enums/            # 枚举类
│   ├── config/              # 配置类
│   │   ├── MybatisPlusConfig.java
│   │   ├── FlowableConfig.java
│   │   ├── Knife4jConfig.java
│   │   └── GlobalExceptionHandler.java
│   ├── controller/          # 控制层
│   │   ├── LoanApplicationController.java
│   │   ├── ApprovalTaskController.java
│   │   └── WorkflowController.java
│   ├── delegate/            # Flowable 服务任务
│   │   ├── CreditQueryDelegate.java
│   │   ├── AntiFraudDelegate.java
│   │   ├── CreditScoringDelegate.java
│   │   ├── LimitCalculationDelegate.java
│   │   └── ApprovalNotificationDelegate.java
│   ├── dto/                 # 数据传输对象
│   ├── entity/              # 实体类
│   ├── init/                # 初始化类
│   ├── mapper/              # MyBatis Mapper
│   └── service/             # 业务服务
│       ├── impl/            # 服务实现
│       ├── CreditQueryService.java
│       ├── AntiFraudService.java
│       ├── CreditScoringService.java
│       ├── LimitCalculationService.java
│       ├── LoanApplicationService.java
│       ├── ApprovalTaskService.java
│       └── WorkflowService.java
├── src/main/resources/
│   ├── processes/           # BPMN 流程定义
│   │   └── credit-approval-process.bpmn20.xml
│   ├── db/                  # 数据库脚本
│   │   └── schema.sql
│   ├── application.yml      # 应用配置
│   └── logback-spring.xml   # 日志配置
└── pom.xml
```

## 快速开始

### 1. 数据库准备

创建数据库并执行初始化脚本：

```sql
CREATE DATABASE credit_flow DEFAULT CHARACTER SET utf8mb4;
USE credit_flow;
SOURCE src/main/resources/db/schema.sql;
```

### 2. 修改配置

修改 `application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/credit_flow?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 启动应用

```bash
java -jar target/credit-flow-decider.jar
```

或者使用 Maven 启动：

```bash
mvn spring-boot:run
```

### 5. 访问接口文档

启动后访问：http://localhost:8080/credit-flow/doc.html

## API 接口说明

### 贷款申请管理

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/application/submit` | POST | 提交贷款申请 |
| `/api/application/{id}` | GET | 根据ID查询申请详情 |
| `/api/application/no/{applicationNo}` | GET | 根据编号查询申请详情 |
| `/api/application/list` | POST | 分页查询申请列表 |

### 审批任务管理

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/approval/todo` | GET | 查询待办任务 |
| `/api/approval/task/{taskId}` | GET | 查询任务详情 |
| `/api/approval/manual-review/complete` | POST | 完成人工复核 |
| `/api/approval/final-approval/complete` | POST | 完成终审 |
| `/api/approval/task/claim` | POST | 签收任务 |
| `/api/approval/task/unclaim` | POST | 取消签收 |
| `/api/approval/task/delegate` | POST | 转办任务 |
| `/api/approval/history/{applicationNo}` | GET | 查询审批历史 |

### 流程定义管理

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/workflow/deploy` | POST | 部署流程定义（XML） |
| `/api/workflow/deploy/file` | POST | 部署流程定义（文件） |
| `/api/workflow/definitions` | GET | 查询流程定义列表 |
| `/api/workflow/deployments` | GET | 查询部署列表 |
| `/api/workflow/suspend/{processInstanceId}` | POST | 挂起流程实例 |
| `/api/workflow/activate/{processInstanceId}` | POST | 恢复流程实例 |
| `/api/workflow/terminate/{processInstanceId}` | POST | 终止流程实例 |
| `/api/workflow/variables/{processInstanceId}` | GET | 查询流程变量 |
| `/api/workflow/variables/{processInstanceId}` | POST | 设置流程变量 |
| `/api/workflow/activity/{processInstanceId}` | GET | 查询当前节点 |
| `/api/workflow/export/{processDefinitionId}` | GET | 导出流程定义XML |
| `/api/workflow/diagram/{processInstanceId}` | GET | 查看流程图 |

## 审批流程说明

### 流程节点

1. **提交申请** - 客户提交贷款申请
2. **征信查询** - 调用征信接口获取客户征信信息
3. **反欺诈校验** - 执行反欺诈规则检查
4. **反欺诈结果判断** - 条件分支，欺诈直接拒绝
5. **信用评分** - 根据多维度进行信用评分
6. **评分结果判断** - 条件分支，评分不足直接拒绝
7. **额度计算** - 计算授信额度和利率
8. **复核判断** - 根据金额和风险判断是否需要人工复核
9. **人工复核** - 高风险或大额申请人工审核
10. **复核结果** - 条件分支
11. **终审** - 最终审批决策
12. **终审结果** - 条件分支
13. **审批通过通知** - 发送审批通过通知
14. **结束** - 审批通过或拒绝

### 条件分支规则

- **反欺诈拒绝**：欺诈评分 >= 80 或高风险规则命中
- **评分不足拒绝**：信用评分 < 600 分
- **人工复核**：授信额度 >= 20万 或 信用评分 < 500 或 高风险
- **高风险拒绝**：高风险且触发反欺诈告警

### 额度计算规则

- 基础额度 = 月收入 × 12 × 评分系数 × 风险系数 × DTI系数
- 评分系数：A(1.2) > B(1.0) > C(0.8) > D(0.5) > E(0.3)
- 风险系数：低(1.0) > 中(0.7) > 高(0.4)
- DTI系数：<20%(1.0) > 20-35%(0.9) > 35-50%(0.7) > 50-65%(0.5) > 65%(0.3)
- 额度上限：1000元 - 50万元

### 利率计算规则

- 基准利率：12%
- 评分调整：750+(-4%), 700-749(-2%), 650-699(0%), 600-649(+2%), 600以下(+4%)
- 风险调整：中风险(+1%), 高风险(+3%)
- 利率区间：6% - 24%

## 评分卡维度

| 维度 | 权重 | 最高分 | 说明 |
|------|------|--------|------|
| 信用历史 | 35% | 350 | 逾期次数等 |
| 还款能力 | 30% | 300 | 月收入水平 |
| 负债比例 | 20% | 200 | 月负债/月收入 |
| 个人信息 | 15% | 150 | 年龄、房产、车辆、学历、工作年限 |

## 运行与测试

### 测试流程

1. 提交贷款申请，系统自动启动审批流程
2. 系统自动执行征信查询、反欺诈校验、信用评分、额度计算
3. 如果需要人工复核，任务进入待办列表
4. 审批人员完成人工复核和终审
5. 系统发送审批通过通知，流程结束

### 测试数据

提交申请测试：

```bash
curl -X POST http://localhost:8080/credit-flow/api/application/submit \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST001",
    "customerName": "张三",
    "idCard": "110101199001011234",
    "phone": "13800138000",
    "loanAmount": 150000,
    "loanTerm": 36,
    "loanPurpose": "装修贷款",
    "monthlyIncome": 25000,
    "monthlyDebt": 5000,
    "age": 35,
    "educationLevel": 4,
    "workYears": 10,
    "hasHouse": true,
    "hasCar": true,
    "ipAddress": "192.168.1.100",
    "submitBy": "test_user"
  }'
```

## 注意事项

1. 本项目中的征信接口为模拟实现，生产环境需对接真实的征信服务
2. 反欺诈规则引擎为简化实现，生产环境建议集成专业的反欺诈系统（如决策引擎）
3. 数据库表中的反欺诈规则和评分卡规则可根据业务需求动态调整
4. Flowable 会自动创建其所需的数据库表，无需手动创建
5. 生产环境建议启用 Flowable 的异步执行器和历史清理策略

## 版本管理

- BPMN 文件建议纳入 Git 版本控制
- 每次部署流程定义会自动递增版本号
- 正在运行的流程实例将继续使用旧版本，新启动的实例使用最新版本
- 支持导出流程定义 XML，便于在不同环境间迁移
