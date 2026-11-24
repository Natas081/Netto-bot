package com.linkguard.service;

import com.linkguard.TestSupport.MutableClock;
import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import com.linkguard.dto.ScanStats;
import com.linkguard.dto.VirusTotalReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LinkClassificationServiceTest {

    private final MutableClock clock = new MutableClock();
    private final LinkClassificationService classifier = new LinkClassificationService(clock);

    @ParameterizedTest
    @CsvSource({
            "0,0,60,15,CONFIÁVEL",
            "0,2,60,13,SUSPEITO",
            "1,2,60,12,PERIGOSO",
            "0,0,0,75,INCONCLUSIVO",
            "0,0,0,0,INCONCLUSIVO"
    })
    void classifiesOnlyFromEngineVerdicts(int malicious, int suspicious, int harmless, int undetected, Status expected) {
        ScanStats stats = new ScanStats(malicious, suspicious, harmless, undetected);
        ChatResponse response = classifier.classify("example.com", new VirusTotalReport(stats, clock.instant()));
        assertThat(response.status()).isEqualTo(expected);
        assertThat(response.stats()).isEqualTo(stats);
        assertThat(response.analyzedAt()).isEqualTo(clock.instant());
        assertThat(response.source()).isEqualTo("VirusTotal");
        assertThat(response.reply()).doesNotContain("100%");
    }

    @Test
    void missingStatsOrDateCannotProduceACleanVerdict() {
        assertThat(classifier.classify("example.com", new VirusTotalReport(null, clock.instant())).status())
                .isEqualTo(Status.INCONCLUSIVO);
        assertThat(classifier.classify("example.com", new VirusTotalReport(new ScanStats(0, 0, 50, 10), null)).status())
                .isEqualTo(Status.INCONCLUSIVO);
    }
}
