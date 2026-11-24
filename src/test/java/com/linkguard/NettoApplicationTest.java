package com.linkguard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "virustotal.api-key=")
@AutoConfigureMockMvc
class NettoApplicationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void runsTheCompleteBackendWithAnExplicitMissingKeyResponse() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"https://example.com/application"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCONCLUSIVO"))
                .andExpect(jsonPath("$.reply").value(containsString("VIRUSTOTAL_API_KEY")))
                .andExpect(jsonPath("$.stats").doesNotExist());
    }

    @Test
    void springServesTheFrontendAndItsAssets() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Netto")));
        mvc.perform(get("/style.css")).andExpect(status().isOk());
        mvc.perform(get("/script.js")).andExpect(status().isOk());
        mvc.perform(get("/fonts/inter-latin.woff2")).andExpect(status().isOk());
    }
}
