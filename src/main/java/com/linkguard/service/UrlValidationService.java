package com.linkguard.service;

import com.linkguard.exception.InvalidUrlException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class UrlValidationService {

    private static final String INVALID_URL = "Envie uma URL HTTP ou HTTPS válida, como https://exemplo.com.";
    private static final String PRIVATE_URL = "Envie um endereço público. Links locais, privados ou com credenciais não são aceitos.";
    private static final List<String> PRIVATE_SUFFIXES = List.of(
            "localhost", "local", "localdomain", "internal", "intranet", "lan", "home",
            "corp", "private", "test", "invalid", "example", "onion", "arpa");

    public URI validate(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidUrlException("Digite ou cole um link para verificar.");
        }
        if (message.length() > 2048) {
            throw new InvalidUrlException("O link pode ter até 2.048 caracteres.");
        }
        try {
            URI input = new URI(message.strip());
            String scheme = input.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new InvalidUrlException(INVALID_URL);
            }
            String authority = input.getRawAuthority();
            if (authority == null || authority.isEmpty()) {
                throw new InvalidUrlException(INVALID_URL);
            }
            if (authority.contains("@") || authority.contains("%")) {
                throw new InvalidUrlException(PRIVATE_URL);
            }
            String asciiAuthority = authority;
            if (!authority.startsWith("[")) {
                int portStart = authority.indexOf(':');
                int hostEnd = portStart == -1 ? authority.length() : portStart;
                asciiAuthority = IDN.toASCII(authority.substring(0, hostEnd), IDN.USE_STD3_ASCII_RULES)
                        + authority.substring(hostEnd);
            }
            // Parse authority independently to preserve the original path and query encoding.
            URI server = new URI(scheme + "://" + asciiAuthority).parseServerAuthority();
            String host = server.getHost();
            if (host == null || server.getPort() == 0 || server.getPort() > 65535) {
                throw new InvalidUrlException(INVALID_URL);
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            validatePublicHost(host);
            String port = server.getPort() == -1 ? "" : ":" + server.getPort();
            String path = input.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            String query = input.getRawQuery() == null ? "" : "?" + input.getRawQuery();
            String fragment = input.getRawFragment() == null ? "" : "#" + input.getRawFragment();
            return URI.create(new URI(scheme.toLowerCase(Locale.ROOT) + "://" + host
                    + port + path + query + fragment).toASCIIString());
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new InvalidUrlException(INVALID_URL);
        }
    }

    private void validatePublicHost(String host) {
        if (host.startsWith("[")) {
            try {
                InetAddress address = InetAddress.getByName(host);
                byte[] bytes = address.getAddress();
                // Only global unicast IPv6, excluding special-purpose and transition ranges.
                if (bytes.length != 16 || (bytes[0] & 0xe0) != 0x20
                        || (bytes[0] == 0x20 && bytes[1] == 0x02)
                        || (bytes[0] == 0x20 && bytes[1] == 0x01 && (bytes[2] & 0xfe) == 0)
                        || (bytes[0] == 0x20 && bytes[1] == 0x01
                        && bytes[2] == 0x0d && (bytes[3] & 0xff) == 0xb8)
                        || (bytes[0] == 0x3f && (bytes[1] & 0xff) == 0xff && (bytes[2] & 0xf0) == 0)) {
                    throw new InvalidUrlException(PRIVATE_URL);
                }
                return;
            } catch (UnknownHostException ex) {
                throw new InvalidUrlException(INVALID_URL);
            }
        }
        if (host.matches("[0-9.]+")) {
            validateIpv4(host);
            return;
        }
        if (!host.contains(".") || host.matches("(?i)(0x[0-9a-f]+|[0-9]+)(\\.(0x[0-9a-f]+|[0-9]+))*")
                || host.substring(host.lastIndexOf('.') + 1).matches("[0-9]+")) {
            throw new InvalidUrlException(PRIVATE_URL);
        }
        if (PRIVATE_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix))) {
            throw new InvalidUrlException(PRIVATE_URL);
        }
    }

    private void validateIpv4(String host) {
        String[] segments = host.split("\\.", -1);
        if (segments.length != 4) throw new InvalidUrlException(PRIVATE_URL);
        int[] octets = new int[4];
        for (int i = 0; i < octets.length; i++) {
            if (segments[i].isEmpty() || segments[i].length() > 3
                    || (segments[i].length() > 1 && segments[i].startsWith("0"))) {
                throw new InvalidUrlException(PRIVATE_URL);
            }
            octets[i] = Integer.parseInt(segments[i]);
            if (octets[i] > 255) throw new InvalidUrlException(INVALID_URL);
        }
        int first = octets[0];
        int second = octets[1];
        int third = octets[2];
        if (first == 0 || first == 10 || first == 127 || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && (second == 168 || (second == 0 && (third == 0 || third == 2))))
                || (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                || (first == 203 && second == 0 && third == 113)) {
            throw new InvalidUrlException(PRIVATE_URL);
        }
    }
}
