package com.linkguard.controller;

import com.linkguard.client.VirusTotalClient;
import com.linkguard.config.VirusTotalConfig;
import com.linkguard.dto.ScanStats;
import com.linkguard.dto.VirusTotalReport;
import com.linkguard.service.LinkAnalysisService;
import com.linkguard.service.LinkClassificationService;
import com.linkguard.service.SecurityTipService;
import com.linkguard.service.UrlValidationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ChatController.class, SecurityTipController.class})
@Import({LinkAnalysisService.class, LinkClassificationService.class, UrlValidationService.class,
        SecurityTipService.class, VirusTotalConfig.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VirusTotalClient client;

    @Test
    void endpointReturnsTheReportWithoutExposingApiCredentials() throws Exception {
        when(client.getReport(any())).thenReturn(Optional.of(new VirusTotalReport(new ScanStats(0, 0, 65, 10), Instant.now())));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"https://example.com/controller"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("CONFIÁVEL"))
                .andExpect(jsonPath("$.stats.harmless").value(65))
                .andExpect(jsonPath("$.stats.undetected").value(10))
                .andExpect(jsonPath("$.source").value("VirusTotal"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void rejectsPrivateLinksBeforeCallingTheProvider() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"http://127.0.0.1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INCONCLUSIVO"))
                .andExpect(jsonPath("$.reply").isNotEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void malformedJsonReturnsAFriendlyMessage() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reply").value("Envie um link no campo message do JSON."));
    }

    @Test
    void emptyBodyReturnsAFriendlyMessage() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reply").value("Digite ou cole um link para verificar."));
    }

    @Test
    void unknownAndInvalidAnalysisTicketsAreHandled() throws Exception {
        mvc.perform(get("/api/chat/analyses/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCONCLUSIVO"));
        mvc.perform(get("/api/chat/analyses/invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reply").isNotEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void randomTipsAreServedFromTheBackendWithoutCaching() throws Exception {
        mvc.perform(get("/api/tips/random"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tip").isString())
                .andExpect(jsonPath("$.tip").isNotEmpty());
        verifyNoInteractions(client);
    }
}
