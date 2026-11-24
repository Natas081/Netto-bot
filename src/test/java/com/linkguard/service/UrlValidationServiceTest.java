package com.linkguard.service;

import com.linkguard.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidationServiceTest {

    private final UrlValidationService validator = new UrlValidationService();

    @ParameterizedTest
    @CsvSource({
            "https://example.com, https://example.com/",
            "' HTTP://EXAMPLE.COM/Path?a=B ', http://example.com/Path?a=B",
            "https://exémplo.com:443/a, https://xn--exmplo-cva.com:443/a",
            "https://example.com./a, https://example.com/a",
            "https://8.8.8.8/path, https://8.8.8.8/path",
            "https://[2606:4700:4700::1111]/, https://[2606:4700:4700::1111]/"
    })
    void acceptsPublicHttpUrlsWithoutChangingPathOrQuery(String input, String normalized) {
        assertThat(validator.validate(input).toASCIIString()).isEqualTo(normalized);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Olá", "example.com", "https:example.com", "https:///example.com",
            "ftp://example.com/", "javascript:alert(1)", "https://example.com/a b",
            "https://example.com/%xx", "https://example.com:65536", "https://example.com:0",
            "https://example.com:-1", "https://example..com", "https://example.com:erro"})
    void rejectsInvalidInputs(String input) {
        assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost", "http://localhost.", "http://sub.localhost", "https://printer",
            "https://router.local", "https://api.internal", "https://bank.home.arpa", "https://service.lan",
            "http://127.0.0.1", "http://0.0.0.0", "http://10.2.3.4", "http://172.16.0.1",
            "http://172.31.255.255", "https://192.168.1.1", "http://169.254.169.254",
            "http://100.64.0.1", "http://198.18.0.1", "http://192.0.2.1", "http://203.0.113.1",
            "http://198.51.100.1", "http://224.0.0.1", "http://255.255.255.255",
            "http://127.1", "http://2130706433", "http://0177.0.0.1", "http://0x7f000001",
            "http://0x7f.0.0.1", "http://[::1]", "http://[::]", "http://[fc00::1]",
            "http://[fe80::1]", "http://[::ffff:127.0.0.1]", "http://[2001:db8::1]",
            "http://[2002:7f00:1::]", "http://[fe80::1%25eth0]",
            "https://user:secret@example.com", "https://user@example.com",
            "https://user%40example.com", "http://localhost%2e", "https://name.test"
    })
    void rejectsLocalPrivateReservedAndCredentialUrls(String input) {
        assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void enforcesLengthAtTheBoundary() {
        String prefix = "https://example.com/";
        String limit = prefix + "a".repeat(2048 - prefix.length());
        assertThat(validator.validate(limit).toString()).hasSize(2048);
        assertThatThrownBy(() -> validator.validate(limit + "a"))
                .isInstanceOf(InvalidUrlException.class).hasMessageContaining("2.048");
    }
}
