package com.employger;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Gives every request from the auto-configured MockMvc a valid CSRF token,
 * so tests about business rules don't each have to add one.
 *
 * CSRF itself is tested in CsrfProtectionIntegrationTest, which builds its
 * own MockMvc without this default.
 */
@Configuration
class CsrfTestConfiguration {

    @Bean
    MockMvcBuilderCustomizer csrfByDefault() {
        return builder -> builder.defaultRequest(get("/").with(csrf()));
    }
}
