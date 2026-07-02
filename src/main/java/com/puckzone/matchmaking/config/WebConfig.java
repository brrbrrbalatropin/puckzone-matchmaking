package com.puckzone.matchmaking.config;

import com.puckzone.matchmaking.security.AuthenticatedUserArgumentResolver;
import com.puckzone.matchmaking.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtProperties properties) {
        var registration = new FilterRegistrationBean<>(new JwtAuthFilter(properties));
        // Solo protege la cola; actuator y demás quedan por fuera.
        registration.addUrlPatterns("/queue", "/queue/*");
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserArgumentResolver());
    }
}
