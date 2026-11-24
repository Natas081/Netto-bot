package com.linkguard.controller;

import com.linkguard.service.LinkAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(LinkAnalysisService.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsTheSimulatedClassificationAsJson() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"https://site-seguro.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIÁVEL"))
                .andExpect(jsonPath("$.reply").value("Este link foi classificado como confiável na simulação."));
    }

    @Test
    void returnsAFriendlyErrorForInvalidLinks() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Olá"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALIDO"))
                .andExpect(jsonPath("$.reply").value("Envie um link HTTP ou HTTPS válido, como https://exemplo.com."));
    }

    @Test
    void returnsAFriendlyErrorForMalformedJson() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALIDO"))
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }
}
