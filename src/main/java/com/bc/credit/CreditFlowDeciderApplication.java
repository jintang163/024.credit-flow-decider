package com.bc.credit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableAsync
@EnableTransactionManagement
@MapperScan("com.bc.credit.mapper")
@SpringBootApplication
public class CreditFlowDeciderApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditFlowDeciderApplication.class, args);
    }
}
