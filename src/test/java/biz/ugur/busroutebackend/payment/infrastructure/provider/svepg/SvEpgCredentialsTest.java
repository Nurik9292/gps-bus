package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SvEpgCredentialsTest {

    @Test
    void toString_doesNotLeakPassword() {
        SvEpgCredentials creds = new SvEpgCredentials(
                "https://epg.example.com", "user1", "Super$ecretP4ss", "t1", "p1");

        String rendered = creds.toString();

        assertThat(rendered).contains("user1");
        assertThat(rendered).contains("https://epg.example.com");
        assertThat(rendered).doesNotContain("Super$ecretP4ss");
        assertThat(rendered).contains("password=***");
    }

    @Test
    void password_accessor_returnsActualValue() {
        SvEpgCredentials creds = new SvEpgCredentials(
                "https://epg.example.com", "user1", "Super$ecretP4ss", "t1", "p1");

        assertThat(creds.password()).isEqualTo("Super$ecretP4ss");
    }

    @Test
    void bankToString_doesNotLeakPassword() {
        SvEpgProperties.Bank bank = new SvEpgProperties.Bank();
        bank.setBaseUrl("https://epg.example.com");
        bank.setUserName("user1");
        bank.setPassword("Super$ecretP4ss");
        bank.setTerminalId("t1");
        bank.setPid("p1");
        bank.setEnabled(true);

        String rendered = bank.toString();

        assertThat(rendered).doesNotContain("Super$ecretP4ss");
        assertThat(rendered).contains("user1");
    }
}
