
package io.github.inaciofragalli.rivet.security;

import io.github.inaciofragalli.rivet.application.ApplicationService;
import io.github.inaciofragalli.rivet.application.internal.ApplicationController;
import io.github.inaciofragalli.rivet.image.ImageArtifactService;
import io.github.inaciofragalli.rivet.image.internal.ImageArtifactController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                ApplicationController.class,
                ImageArtifactController.class
        },
        properties = "rivet.cors.allowed-origin=http://localhost:5173"
)
@Import(DevSecurityConfig.class)
@ActiveProfiles("dev")
class CorsConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private ImageArtifactService imageArtifactService;

    @Test
    void shouldAllowImageUploadPreflight() throws Exception {
        mockMvc.perform(options("/api/image-artifacts")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }

    @Test
    void shouldAllowApplicationPreflight() throws Exception {
        mockMvc.perform(options("/api/applications")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ));
    }

    @Test
    void shouldRejectUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/image-artifacts")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
