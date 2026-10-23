package com.employger;

import com.employger.entity.Earning;
import com.employger.service.contribution.StatutoryContribution;
import com.employger.service.earning.DeMinimisBenefit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spring finds every contribution and de minimis benefit by its parent type. */
@SpringBootTest
class OopWiringIntegrationTest {

    @Autowired
    private List<StatutoryContribution> contributions;

    @Autowired
    private List<DeMinimisBenefit> deMinimisBenefits;

    @Test
    void allThreeStatutoryContributionsAreInjected() {
        assertThat(contributions)
                .extracting(StatutoryContribution::type)
                .containsExactlyInAnyOrder(StatutoryContribution.Type.values());
    }

    @Test
    void everyDeMinimisTypeHasOneBenefitBean() {
        assertThat(deMinimisBenefits)
                .extracting(DeMinimisBenefit::type)
                .containsExactlyInAnyOrder(
                        Earning.Type.DE_MINIMIS_RICE,
                        Earning.Type.DE_MINIMIS_CLOTHING,
                        Earning.Type.DE_MINIMIS_MEDICAL,
                        Earning.Type.DE_MINIMIS_LEAVE);
    }
}
