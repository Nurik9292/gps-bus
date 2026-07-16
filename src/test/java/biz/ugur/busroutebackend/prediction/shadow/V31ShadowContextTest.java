package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.infrastructure.config.V31ShadowConfig;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class V31ShadowContextTest {

    @Configuration
    static class CacheStub {
        @Bean
        RouteGeometryCache routeGeometryCache() {
            return Mockito.mock(RouteGeometryCache.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(CacheStub.class, V31ShadowConfig.class);

    @Test
    void flagOffMeansNoV31Beans() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(V31ShadowTap.class);
            assertThat(ctx).doesNotHaveBean(V31ShadowService.class);
            assertThat(ctx).doesNotHaveBean(V31RouteLines.class);
        });
    }

    @Test
    void flagOffExplicitFalseMeansNoV31Beans() {
        runner.withPropertyValues("app.prediction.v31.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(V31ShadowTap.class);
            assertThat(ctx).doesNotHaveBean(V31ShadowService.class);
        });
    }

    @Test
    void flagOnInstantiatesShadowBeans() {
        runner.withPropertyValues("app.prediction.v31.enabled=true",
                        "app.prediction.v31.log-dir=target/ws_pred_v31_test")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(V31ShadowTap.class);
                    assertThat(ctx).hasSingleBean(V31ShadowService.class);
                    assertThat(ctx.getBean(V31ShadowService.class).clock()).isNotNull();
                });
    }
}
