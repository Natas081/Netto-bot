package com.linkguard.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTipServiceTest {

    @Test
    void providesAtLeastTwentyFiveDistinctBackendTips() {
        assertThat(SecurityTipService.TIPS).hasSizeGreaterThanOrEqualTo(25).doesNotHaveDuplicates();
        assertThat(SecurityTipService.TIPS).allSatisfy(tip -> assertThat(tip).isNotBlank());
        SecurityTipService service = new SecurityTipService();
        for (int i = 0; i < 50; i++) assertThat(service.randomTip()).isIn(SecurityTipService.TIPS);
    }
}
