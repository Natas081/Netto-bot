package com.linkguard.service;

import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LinkAnalysisService {

    private static final int MAX_MESSAGE_LENGTH = 2048;
    private static final String INVALID_LINK = "Envie um link HTTP ou HTTPS válido, como https://exemplo.com.";
    private static final List<Status> SIMULATED_STATUSES = List.of(Status.CONFIÁVEL, Status.SUSPEITO, Status.PERIGOSO);

    public ChatResponse analyze(String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            return ChatResponse.invalid("O link pode ter até 2.048 caracteres.");
        }

        String link = message == null ? "" : message.strip();
        if (link.isEmpty()) {
            return ChatResponse.invalid("Digite ou cole um link para iniciar a simulação.");
        }
        if (!isHttpLink(link)) {
            return ChatResponse.invalid(INVALID_LINK);
        }

        Status status = classify(link.toLowerCase(Locale.ROOT));
        String reply = switch (status) {
            case CONFIÁVEL -> "Este link foi classificado como confiável na simulação.";
            case SUSPEITO -> "Atenção! Este link foi classificado como suspeito na simulação.";
            case PERIGOSO -> "Alerta! Este link foi classificado como perigoso na simulação. Evite acessá-lo.";
            case INVALIDO -> throw new IllegalStateException("A classificação simulada exige uma URL válida.");
        };
        return new ChatResponse(status, reply);
    }

    private boolean isHttpLink(String link) {
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            URI server = withAsciiHost(uri);
            return server.getHost() != null && server.getPort() <= 65535;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private URI withAsciiHost(URI uri) throws URISyntaxException {
        String authority = uri.getRawAuthority();
        if (uri.getHost() != null || authority == null || authority.chars().allMatch(c -> c < 128)) {
            return uri;
        }

        int hostStart = authority.lastIndexOf('@') + 1;
        int portStart = authority.indexOf(':', hostStart);
        int hostEnd = portStart == -1 ? authority.length() : portStart;
        String host = IDN.toASCII(authority.substring(hostStart, hostEnd), IDN.USE_STD3_ASCII_RULES);
        return new URI(uri.getScheme() + "://" + authority.substring(0, hostStart)
                + host + authority.substring(hostEnd)).parseServerAuthority();
    }

    private Status classify(String link) {
        if (link.contains("perigoso")) {
            return Status.PERIGOSO;
        }
        if (link.contains("suspeito")) {
            return Status.SUSPEITO;
        }
        if (link.contains("seguro")) {
            return Status.CONFIÁVEL;
        }
        return SIMULATED_STATUSES.get(Math.floorMod(link.hashCode(), SIMULATED_STATUSES.size()));
    }
}
