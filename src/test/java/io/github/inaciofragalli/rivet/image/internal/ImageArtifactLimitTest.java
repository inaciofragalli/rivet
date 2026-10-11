package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageArtifactController.class)
@Import(ImageArtifactExceptionHandler.class)
@ActiveProfiles("test")
@WithMockUser
class ImageArtifactLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageArtifactService imageArtifactService;

    @Test
    void shouldReturn413WhenStorageRejectsOversizedArchive()
            throws Exception {

        doThrow(new ImageArchiveTooLargeException())
                .when(imageArtifactService)
                .upload(any(InputStream.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.tar",
                "application/x-tar",
                new byte[]{1}
        );

        mockMvc.perform(
                        multipart("/api/image-artifacts")
                                .file(file)
                                .with(csrf())
                )
                .andExpectAll(
                        status().is(413),
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        ),
                        jsonPath("$.title").value("Upload too large")
                );
    }
}
