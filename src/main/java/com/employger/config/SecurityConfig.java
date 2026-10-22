package com.employger.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
public class SecurityConfig {

        // Employee, attendance, overtime, pay-cycle, payroll, and payslip data.
        private static final String[] PAYROLL_DATA_APIS = {
                        "/api/employees/**",
                        "/api/attendance/**",
                        "/api/overtime/**",
                        "/api/pay-cycles/**",
                        "/api/payroll/**",
                        "/api/payroll-calculation/**",
                        "/api/payslips/**"
        };

        private static void writePlainText(
                        HttpServletResponse response,
                        int status,
                        String message) throws IOException {

                response.setStatus(status);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(message);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .csrf(AbstractHttpConfigurer::disable)

                                .authorizeHttpRequests(auth -> auth

                                                // Static frontend files contain no data; every
                                                // page loads its data from the protected APIs below.
                                                .requestMatchers("/", "/*.html", "/*.css", "/*.js")
                                                .permitAll()

                                                // Login, logout, and current-user information
                                                .requestMatchers("/api/auth/**")
                                                .permitAll()

                                                // Spring Boot's error page. Without this, any server
                                                // error is replaced by an empty 403 response.
                                                .requestMatchers("/error")
                                                .permitAll()

                                                // User account management
                                                .requestMatchers("/api/users/**")
                                                .hasRole("ADMINISTRATOR")

                                                // System settings
                                                .requestMatchers("/api/settings/**")
                                                .hasRole("ADMINISTRATOR")

                                                // Audit trail (read-only)
                                                .requestMatchers("/api/audit-logs/**")
                                                .hasRole("ADMINISTRATOR")

                                                // Self-service: any logged-in user, limited by
                                                // the service to the employee linked to that login.
                                                .requestMatchers("/api/me/**")
                                                .authenticated()

                                                // Payroll data: general managers may read it;
                                                // only administrators and HR managers may change it.
                                                .requestMatchers(HttpMethod.GET, PAYROLL_DATA_APIS)
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER",
                                                                "GENERAL_MANAGER")

                                                .requestMatchers(PAYROLL_DATA_APIS)
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER")

                                                // Everything else requires login
                                                .anyRequest().authenticated())

                                // Readable plain-text answers the pages can show:
                                // 401 = log in again, 403 = logged in but not allowed.
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, e) -> writePlainText(
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "Please log in."))
                                                .accessDeniedHandler((request, response, e) -> writePlainText(
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "You do not have permission to perform this action.")))

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