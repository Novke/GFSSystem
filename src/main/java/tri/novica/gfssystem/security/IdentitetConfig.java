package tri.novica.gfssystem.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rezervni {@link IdentitetStudenta} (spec 13.1): {@link NemaIdentiteta} samo kad nijedan drugi bean tog tipa ne postoji.
 * U ovom buildu postoji {@link NalogIdentitet} ({@code @Primary}), pa se rezervni ne pravi.
 */
@Configuration(proxyBeanMethods = false)
public class IdentitetConfig {

    @Bean
    @ConditionalOnMissingBean(IdentitetStudenta.class)
    NemaIdentiteta nemaIdentiteta() {
        return new NemaIdentiteta();
    }
}
