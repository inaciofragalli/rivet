package io.github.inaciofragalli.rivet.security.internal;

import com.jayway.jsonpath.JsonPath;
import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthenticationIntegrationTest {
    // Precisa coincidir com os valores TEST-ONLY adicionados no application-test.properties.
    private static final String TEST_EMAIL = "admin@test.local";
    private static final String TEST_PASSWORD = "TestPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeCsrfTokenToAnonymousClients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.token").isNotEmpty()
                )
                .andReturn();

        assertTrue(result.getRequest().getSession(false) != null);
    }

    @Test
    void shouldRejectUnauthenticatedMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnauthenticatedApplicationAccess() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectLoginWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", TEST_EMAIL)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectLoginWithInvalidCsrf() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(state.session())
                        .header("X-CSRF-TOKEN", "invalid-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", TEST_EMAIL)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectIncorrectPassword() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, "incorrect-password"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepAuthenticationAcrossRequestsUsingTheSession() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        SecurityContext context = assertInstanceOf(SecurityContext.class,
                state.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertTrue(context.getAuthentication().isAuthenticated());
        assertEquals(TEST_EMAIL, context.getAuthentication().getName());

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.email").value(TEST_EMAIL)
                );
    }

    @Test
    void shouldChangeSessionIdAfterSuccessfulLogin() throws Exception {
        CsrfSession state = getCsrfSession();
        String idBeforeLogin = state.session().getId();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        assertNotEquals(idBeforeLogin, state.session().getId());
    }

    @Test
    void shouldRejectStateChangingRequestsWithoutCsrfEvenWhenLoggedIn() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/applications")
                        .session(state.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRequireCsrfForLogout() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout").session(state.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRequireFreshCsrfTokenAfterLogin() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header("X-CSRF-TOKEN", state.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(status().isOk());

        String newToken = fetchCsrfToken(state.session());
        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header("X-CSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());

        assertTrue(state.session().isInvalid());
    }

    @Test
    void shouldInvalidateSessionOnLogout() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(loginRequest(state, TEST_PASSWORD))
                .andExpect(status().isNoContent());

        // Spring Security limpa o token CSRF antigo na autenticação.
        String newToken = fetchCsrfToken(state.session());

        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header("X-CSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());

        assertTrue(state.session().isInvalid());

        // Uma requisição anônima depois do logout não pode herdar autenticação
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // O navegador obtém outra sessão e outro token; não reutiliza a sessão invalidada.
        CsrfSession anonymous = getCsrfSession();
        assertNotEquals(state.session().getId(), anonymous.session().getId());

        mockMvc.perform(loginRequest(new CsrfSession(anonymous.session(), newToken), TEST_PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(loginRequest(anonymous, TEST_PASSWORD))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(anonymous.session()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowLoginWithEmailContainingWhitespaceAndMixedCase() throws Exception {
        CsrfSession state = getCsrfSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(state.session())
                        .header("X-CSRF-TOKEN", state.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "  ADMIN@TEST.LOCAL  ")
                        .param("password", TEST_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.email").value(TEST_EMAIL)
                );
    }

    @Test
    void shouldExposeHealthCheckAnonymouslyWhileKeepingOtherActuatorEndpointsSecured() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    private CsrfSession getCsrfSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            throw new AssertionError("GET /api/auth/csrf did not create a session");
        }

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return new CsrfSession(session, token);
    }

    private String fetchCsrfToken(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            CsrfSession state, String password
    ) {
        return post("/api/auth/login")
                .session(state.session())
                .header("X-CSRF-TOKEN", state.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", TEST_EMAIL)
                .param("password", password);
    }

    private record CsrfSession(MockHttpSession session, String token) {
    }
}
