package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TanatBannerResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TanatBannerResponse parse(String json) throws Exception {
        return objectMapper.readValue(json, TanatBannerResponse.class);
    }

    @Test
    void parsesBannerExactlyAsTanatReturnsIt() throws Exception {
        TanatBannerResponse response = parse("""
                {"message":"","success":true,"data":{"banner":{
                  "lang":"tk",
                  "banner_file":"https://tanat.halkarahil.com/api/storage/serve/9d20ed7d.png",
                  "hash":"96836879-1e37-4c67-b8d0-786c5e55aa01",
                  "url":"https://tanat.halkarahil.com/api/client-data/client-page?hash=96836879"}}}
                """);

        assertThat(response.carriesBanner()).isTrue();
        assertThat(response.bannerOrNull().hash()).isEqualTo("96836879-1e37-4c67-b8d0-786c5e55aa01");
        assertThat(response.bannerOrNull().bannerFile()).endsWith("9d20ed7d.png");
        assertThat(response.bannerOrNull().url()).contains("client-page");
        assertThat(response.bannerOrNull().lang()).isEqualTo("tk");
    }

    @Test
    void emptyBannerMeansNoAdvertisementRatherThanFailure() throws Exception {
        TanatBannerResponse response = parse("""
                {"message":"","success":true,"data":{"banner":{"lang":"","banner_file":"","hash":null}}}
                """);

        assertThat(response.success()).isTrue();
        assertThat(response.carriesBanner()).isFalse();
        assertThat(response.bannerOrNull()).isNull();
    }

    @Test
    void missingDataIsTreatedAsNoBanner() throws Exception {
        assertThat(parse("{\"message\":\"\",\"success\":true}").carriesBanner()).isFalse();
    }

    @Test
    void unsuccessfulResponseNeverYieldsBanner() throws Exception {
        TanatBannerResponse response = parse("""
                {"success":false,"error":{"message":"Error message"}}
                """);

        assertThat(response.carriesBanner()).isFalse();
    }

    @Test
    void unknownFieldsDoNotBreakParsing() throws Exception {
        TanatBannerResponse response = parse("""
                {"message":"","success":true,"extra":123,"data":{"banner":{
                  "lang":"ru","banner_file":"https://x/y.jpg","hash":"h-1","url":"https://x/go","weight":5}}}
                """);

        assertThat(response.carriesBanner()).isTrue();
    }
}
