package com.linkguard.service;

import com.linkguard.client.VirusTotalClient;
import com.linkguard.config.VirusTotalProperties;
import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import com.linkguard.dto.VirusTotalAnalysis;
import com.linkguard.dto.VirusTotalReport;
import com.linkguard.exception.VirusTotalException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LinkAnalysisService {

    private final UrlValidationService validator;
    private final VirusTotalClient client;
    private final LinkClassificationService classifier;
    private final VirusTotalProperties properties;
    private final Clock clock;
    private final Map<String, Entry> cache = new LinkedHashMap<>();

    public LinkAnalysisService(UrlValidationService validator, VirusTotalClient client,
                               LinkClassificationService classifier, VirusTotalProperties properties, Clock clock) {
        this.validator = validator;
        this.client = client;
        this.classifier = classifier;
        this.properties = properties;
        this.clock = clock;
    }

    // One lock coalesces simultaneous submissions and polling in this in-memory prototype.
    public synchronized ChatResponse analyze(String message) {
        URI url = validator.validate(message);
        String key = url.toASCIIString();
        pruneCache();
        Entry existing = cache.get(key);
        if (existing != null) return existing.response;

        evictCompletedEntryIfFull();
        if (cache.size() >= properties.cacheMaxEntries()) {
            return ChatResponse.inconclusive(url.getHost(), "Há muitas verificações em andamento. Tente novamente mais tarde.");
        }
        Entry entry = new Entry(url, clock.instant());
        try {
            Optional<VirusTotalReport> report = client.getReport(url);
            if (report.isPresent() && isFresh(report.get())) {
                entry.finish(classifier.classify(url.getHost(), report.get()), properties.cacheTtl(), clock.instant());
            } else {
                entry.providerAnalysisId = client.submit(url);
                entry.nextPollAt = clock.instant().plus(properties.pollInterval());
                entry.expiresAt = clock.instant().plus(properties.analysisTimeout()).plusSeconds(60);
                entry.response = pending(entry);
            }
        } catch (VirusTotalException ex) {
            entry.finish(failure(url.getHost(), ex), Duration.ofMinutes(1), clock.instant());
        }
        cache.put(key, entry);
        return entry.response;
    }

    public synchronized ChatResponse poll(UUID analysisId) {
        Entry entry = cache.values().stream().filter(item -> item.ticket.equals(analysisId)).findFirst().orElse(null);
        if (entry == null || !clock.instant().isBefore(entry.expiresAt)) {
            return ChatResponse.inconclusive(null, "Esta verificação expirou. Envie o link novamente para consultar um relatório atualizado.");
        }
        if (entry.response.status() != Status.EM_ANALISE) return entry.response;
        Instant now = clock.instant();
        if (!now.isBefore(entry.createdAt.plus(properties.analysisTimeout())) || entry.polls >= properties.maxPolls()) {
            entry.finish(ChatResponse.inconclusive(entry.url.getHost(),
                    "O VirusTotal ainda não concluiu a verificação dentro do tempo de acompanhamento. Isso não significa que o link seja seguro. Tente novamente mais tarde."),
                    Duration.ofMinutes(1), now);
            return entry.response;
        }
        if (now.isBefore(entry.nextPollAt)) return pending(entry);

        entry.polls++;
        try {
            VirusTotalAnalysis analysis = client.getAnalysis(entry.providerAnalysisId);
            if (analysis.completed()) {
                entry.finish(classifier.classify(entry.url.getHost(), analysis.report()), properties.cacheTtl(), clock.instant());
            } else {
                entry.nextPollAt = clock.instant().plus(properties.pollInterval());
                entry.response = pending(entry);
            }
        } catch (VirusTotalException ex) {
            entry.finish(failure(entry.url.getHost(), ex), Duration.ofMinutes(1), clock.instant());
        }
        return entry.response;
    }

    private boolean isFresh(VirusTotalReport report) {
        return report.stats() != null && report.stats().hasVerdicts() && report.analyzedAt() != null
                && report.analyzedAt().isAfter(clock.instant().minus(properties.reportMaxAge()));
    }

    private ChatResponse pending(Entry entry) {
        long seconds = Math.max(1, Duration.between(clock.instant(), entry.nextPollAt).toSeconds() + 1);
        return new ChatResponse(Status.EM_ANALISE,
                "Estou analisando este link. Aguarde alguns instantes enquanto consulto os mecanismos de segurança.",
                entry.url.getHost(), null, null, "VirusTotal", entry.ticket, (int) seconds);
    }

    private ChatResponse failure(String domain, VirusTotalException ex) {
        String reason = switch (ex.reason()) {
            case NOT_CONFIGURED -> "A chave VIRUSTOTAL_API_KEY ainda não foi configurada no servidor.";
            case UNAUTHORIZED -> "O VirusTotal não autorizou a consulta. Revise a chave e as permissões da conta no servidor.";
            case RATE_LIMIT -> "O limite de consultas ao VirusTotal foi atingido. Tente novamente mais tarde.";
            case UNAVAILABLE, INVALID_RESPONSE -> "Não consegui verificar a segurança deste link no momento. Tente novamente mais tarde.";
        };
        return ChatResponse.inconclusive(domain, reason + " Isso não significa que o link seja seguro.");
    }

    private void pruneCache() {
        Instant now = clock.instant();
        Iterator<Entry> entries = cache.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            if (!now.isBefore(entry.expiresAt)) entries.remove();
        }
    }

    private void evictCompletedEntryIfFull() {
        if (cache.size() >= properties.cacheMaxEntries()) {
            cache.values().stream().filter(item -> item.response.status() != Status.EM_ANALISE)
                    .findFirst().ifPresent(item -> cache.remove(item.url.toASCIIString()));
        }
    }

    private static class Entry {
        final URI url;
        final UUID ticket = UUID.randomUUID();
        final Instant createdAt;
        Instant expiresAt;
        Instant nextPollAt;
        String providerAnalysisId;
        int polls;
        ChatResponse response;

        Entry(URI url, Instant now) {
            this.url = url;
            this.createdAt = now;
        }

        void finish(ChatResponse result, Duration ttl, Instant now) {
            response = result;
            expiresAt = now.plus(ttl);
        }
    }
}
