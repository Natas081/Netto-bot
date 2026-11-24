package com.linkguard.service;

import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LinkAnalysisServiceTest {

    private final LinkAnalysisService service = new LinkAnalysisService();

    @ParameterizedTest
    @CsvSource({
            "https://site-seguro.com, CONFIÁVEL, Este link foi classificado como confiável na simulação.",
            "https://site-suspeito.com, SUSPEITO, Atenção! Este link foi classificado como suspeito na simulação.",
            "https://site-perigoso.com, PERIGOSO, Alerta! Este link foi classificado como perigoso na simulação. Evite acessá-lo."
    })
    void classifiesKeywordsWithTheRequestedReplies(String link, Status status, String reply) {
        assertThat(service.analyze(link)).isEqualTo(new ChatResponse(status, reply));
    }

    @ParameterizedTest
    @CsvSource({
            "'  HTTP://EXEMPLO.COM/SEGURO  ', CONFIÁVEL",
            "https://exémplo.com:443/seguro, CONFIÁVEL",
            "http://[::1]:8080/suspeito, SUSPEITO",
            "https://exemplo.com/seguro?suspeito, SUSPEITO",
            "https://exemplo.com/seguro/suspeito#perigoso, PERIGOSO"
    })
    void handlesDifferentUrlFormatsAndConflictingKeywords(String link, Status expected) {
        assertThat(service.analyze(link).status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "https://exemplo.com/a, CONFIÁVEL",
            "https://exemplo.com/b, SUSPEITO",
            "https://exemplo.com/c, PERIGOSO"
    })
    void assignsDeterministicResultsWithoutKeywords(String link, Status expected) {
        ChatResponse response = service.analyze(link);
        assertThat(response.status()).isEqualTo(expected);
        assertThat(response.reply()).contains("simulação");
        assertThat(service.analyze(link)).isEqualTo(response);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Olá, bot", "exemplo.com", "ftp://exemplo.com",
            "javascript:alert(1)", "https:exemplo.com", "https:///exemplo.com",
            "https://exemplo.com/duas palavras", "https://exemplo.com:65536", "https://exemplo.com/%xx",
            "https://éxemplo.com:erro", "https://éxemplo..com"})
    void rejectsBlankMessagesAndInvalidLinks(String message) {
        ChatResponse response = service.analyze(message);
        assertThat(response.status()).isEqualTo(Status.INVALIDO);
        assertThat(response.reply()).isNotBlank();
    }

    @Test
    void enforcesTheLengthLimit() {
        String prefix = "https://exemplo.com/seguro/";
        String limit = prefix + "a".repeat(2048 - prefix.length());
        assertThat(service.analyze(limit).status()).isEqualTo(Status.CONFIÁVEL);
        assertThat(service.analyze(limit + "a")).isEqualTo(ChatResponse.invalid("O link pode ter até 2.048 caracteres."));
    }
}
