package biz.ugur.busroutebackend.shared.infrastructure.web;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface DeprecatedApi {
    /**
     * Версия, в которой API стал deprecated
     */
    String since();

    /**
     * Версия, в которой API будет удален
     */
    String removeIn();

    /**
     * Альтернативный endpoint для использования
     */
    String useInstead() default "";

    /**
     * Дата sunset (когда будет удален)
     */
    String sunsetDate() default "";

    /**
     * Дополнительное описание
     */
    String description() default "";
}
