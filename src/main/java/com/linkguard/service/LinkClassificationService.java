package com.linkguard.service;

import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import com.linkguard.dto.ScanStats;
import com.linkguard.dto.VirusTotalReport;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class LinkClassificationService {

    private final Clock clock;

    public LinkClassificationService(Clock clock) {
        this.clock = clock;
    }

    public ChatResponse classify(String domain, VirusTotalReport report) {
        ScanStats stats = report.stats();
        Status status = Status.INCONCLUSIVO;
        String reply = "O relatório não contém verificações suficientes para concluir a análise. Isso não significa que o link seja seguro.";
        if (stats != null && stats.hasVerdicts() && report.analyzedAt() != null
                && !report.analyzedAt().isAfter(clock.instant().plusSeconds(300))) {
            if (stats.malicious() > 0) {
                status = Status.PERIGOSO;
                reply = "Alerta! Este link foi sinalizado como potencialmente malicioso por mecanismos de segurança. Evite acessá-lo ou fornecer informações pessoais.";
            } else if (stats.suspicious() > 0) {
                status = Status.SUSPEITO;
                reply = "Atenção! Foram identificados indícios suspeitos neste link. Recomendamos cautela antes de acessá-lo.";
            } else {
                status = Status.CONFIÁVEL;
                reply = "Nenhuma ameaça foi identificada pelos mecanismos de segurança consultados. Mesmo assim, tenha cuidado ao fornecer informações pessoais.";
            }
        }
        return new ChatResponse(status, reply, domain, stats, report.analyzedAt(), "VirusTotal", null, null);
    }
}
