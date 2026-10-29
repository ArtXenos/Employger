package com.employger.service.kiosk;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BadgeCodeTest {

    @Test
    void generatedCodesHaveTheBadgeFormatAndDontRepeat() {
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String code = BadgeCode.generate().value();
            assertThat(code).matches("EMP(-[2-9A-HJ-NP-Z]{4}){4}");
            seen.add(code);
        }

        assertThat(seen).hasSize(1000);
    }

    @Test
    void typedCodesAreReadWhateverTheCaseSpacesAndDashes() {
        assertThat(BadgeCode.parse(" emp 7q4k m2xp-9d3h k8rt "))
                .contains(new BadgeCode("EMP-7Q4K-M2XP-9D3H-K8RT"));
        assertThat(BadgeCode.parse("EMP7Q4KM2XP9D3HK8RT"))
                .contains(new BadgeCode("EMP-7Q4K-M2XP-9D3H-K8RT"));
    }

    @Test
    void anythingElseIsNotABadgeCode() {
        assertThat(BadgeCode.parse(null)).isEmpty();
        assertThat(BadgeCode.parse("")).isEmpty();
        assertThat(BadgeCode.parse("EMP-0001")).isEmpty();
        // 0, O, 1 and I are never used, so they can't be in a real code.
        assertThat(BadgeCode.parse("EMP-0O1I-M2XP-9D3H-K8RT")).isEmpty();
        assertThat(BadgeCode.parse("https://example.test/EMP-7Q4K-M2XP-9D3H-K8RT")).isEmpty();

        assertThatThrownBy(() -> new BadgeCode("EMP-7Q4K")).isInstanceOf(IllegalArgumentException.class);
    }
}
