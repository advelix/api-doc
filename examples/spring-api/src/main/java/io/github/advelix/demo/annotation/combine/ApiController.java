package io.github.advelix.demo.annotation.combine;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 是 {@link RequestMapping} 和 {@link RestController} 的整合注解
 *
 * @author jiangwh
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping
public @interface ApiController {

    /**
     * 请求路径
     */
    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};
}
