package com.marketplace.checkout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    /**
     * Expor o relogio como bean permite que os testes fixem o instante (Clock.fixed) e verifiquem
     * os timestamps do pedido de forma deterministica.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
