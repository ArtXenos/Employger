package com.employger.config;

import com.employger.repository.UserRepository;
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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

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
                        "/api/payslips/**",
                        "/api/holidays/**",
                        "/api/earnings/**",
                        "/api/prior-employers/**"
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
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        UserRepository userRepository) throws Exception {

                http
                                // Before the anonymous filter, so a session it ends
                                // continues as a visitor and gets 401.
                                .addFilterBefore(
                                                new ActiveAccountFilter(userRepository),
                                                AnonymousAuthenticationFilter.class)

                                // Session cookies are sent automatically, so a page on another
                                // site could otherwise submit changes as the logged-in user.
                                // spa(): the token travels in the XSRF-TOKEN cookie, and csrf.js
                                // copies it into the X-XSRF-TOKEN header of every write request.
                                .csrf(csrf -> csrf.spa())

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

                                                // Remittance reports and BIR 2316: they list
                                                // government ID numbers, so not general managers.
                                                .requestMatchers("/api/reports/**")
                                                .hasAnyRole("ADMINISTRATOR", "HR_MANAGER")

                                                // Company overview: totals only, no personal data.
                                                .requestMatchers(HttpMethod.GET, "/api/dashboard/**")
                                                .hasAnyRole(
                                                                "ADMINISTRATOR",
                                                                "HR_MANAGER",
                                                                "GENERAL_MANAGER")

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
                                                                e instanceof CsrfException
                                                                                ? "Your security token is missing or expired. "
                                                                                                + "Please reload the page and try again."
                                                                                : "You do not have permission to perform this action.")))

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