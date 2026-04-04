package biz.ugur.busroutebackend.shared.infrastructure.web;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface DeprecatedApi {
   
    String since();

    String removeIn();

    String useInstead() default "";
   
    String sunsetDate() default "";

    String description() default "";
}
