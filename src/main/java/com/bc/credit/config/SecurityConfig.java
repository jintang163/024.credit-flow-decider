package com.bc.credit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                .antMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                .antMatchers("/doc.html", "/webjars/**", "/v2/api-docs", "/swagger-resources/**", "/favicon.ico").permitAll()
                .antMatchers("/api/loan/**", "/api/approval/**", "/api/workflow/**", "/api/anti-fraud/**", "/api/scoring/**", "/api/monitor/**", "/api/ops/**", "/api/application/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .httpBasic().disable();

        return http.build();
    }
}
