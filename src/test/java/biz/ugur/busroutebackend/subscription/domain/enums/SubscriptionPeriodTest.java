package biz.ugur.busroutebackend.subscription.domain.enums;

import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionPeriodTest {

    @Test
    void from_month_alias_returnsMonthly() {
        assertThat(SubscriptionPeriod.from("month")).isEqualTo(SubscriptionPeriod.MONTHLY);
        assertThat(SubscriptionPeriod.from("MONTH")).isEqualTo(SubscriptionPeriod.MONTHLY);
        assertThat(SubscriptionPeriod.from("Month")).isEqualTo(SubscriptionPeriod.MONTHLY);
    }

    @Test
    void from_year_alias_returnsYearly() {
        assertThat(SubscriptionPeriod.from("year")).isEqualTo(SubscriptionPeriod.YEARLY);
        assertThat(SubscriptionPeriod.from("YEAR")).isEqualTo(SubscriptionPeriod.YEARLY);
    }

    @Test
    void from_canonicalNames_alsoAccepted() {
        assertThat(SubscriptionPeriod.from("MONTHLY")).isEqualTo(SubscriptionPeriod.MONTHLY);
        assertThat(SubscriptionPeriod.from("yearly")).isEqualTo(SubscriptionPeriod.YEARLY);
    }

    @Test
    void from_unknown_throws() {
        assertThatThrownBy(() -> SubscriptionPeriod.from("weekly"))
                .isInstanceOf(SubscriptionValidationException.class)
                .hasMessageContaining("unknown period");
    }

    @Test
    void from_blank_throws() {
        assertThatThrownBy(() -> SubscriptionPeriod.from("  "))
                .isInstanceOf(SubscriptionValidationException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void daysCount_matchesExpected() {
        assertThat(SubscriptionPeriod.MONTHLY.getDaysCount()).isEqualTo(30);
        assertThat(SubscriptionPeriod.YEARLY.getDaysCount()).isEqualTo(365);
    }
}
