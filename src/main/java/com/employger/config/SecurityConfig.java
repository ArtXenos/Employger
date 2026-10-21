package com.employger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .csrf(AbstractHttpConfigurer::disable)

                                .authorizeHttpRequests(auth -> auth

                                                // Public frontend files
                                                .requestMatchers(
                                                                "/",
                                                                "/index.html",
                                                                "/script.js",
                                                                "/style.css",

                                                                "/dashboard.html",
                                                                "/dashboard.css",
                                                                "/dashboard.js",

                                                                "/employees.html",

                                                                "/attendance.html",
                                                                "/attendance.css",
                                                                "/attendance.js",

                                                                "/pay-cycles.html",
                                                                "/pay-cycles.css",
                                                                "/pay-cycles.js",

                                                                "/overtime.html",
                                                                "/overtime.css",
                                                                "/overtime.js",

                                                                "/payroll.html",
                                                                "/payroll.css",
                                                                "/payroll.js",

                                                                "/payslip.html",
                                                                "/payslip.css",
                                                                "/payslip.js",

                                                                "/settings.html",
                                                                "/settings.css",
                                                                "/settings.js")
                                                .permitAll()

                                                // Login, logout, and current-user information
                                                .requestMatchers("/api/auth/**")
                                                .permitAll()

                                                // User account management
                                                .requestMatchers("/api/users/**")
                                                .hasRole("ADMINISTRATOR")

                                                // System settings
                                                .requestMatchers("/api/settings/**")
                                                .hasRole("ADMINISTRATOR")

                                                // Employee management
                                                .requestMatchers("/api/employees/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Attendance management
                                                .requestMatchers("/api/attendance/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Pay cycle management
                                                .requestMatchers("/api/pay-cycles/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Payroll management
                                                .requestMatchers("/api/payroll/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Payroll calculation testing
                                                .requestMatchers("/api/payroll-calculation/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Overtime management
                                                .requestMatchers("/api/overtime/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Payslips embed payroll and employee details,
                                                // so they are restricted like payroll itself.
                                                .requestMatchers("/api/payslips/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Everything else requires login
                                                .anyRequest().authenticated())

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                SessionCreationPolicy.IF_REQUIRED))

                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {

                return authenticationConfiguration
                                .getAuthenticationManager();
        }
}