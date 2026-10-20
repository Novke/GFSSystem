package tri.novica.gfssystem.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Podrazumevani {@link IdentitetStudenta}. Auto-konfiguracija (ime klase je u
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, pa je component scan
 * preskače) se obrađuje posle svih korisničkih beanova, zato {@code @ConditionalOnMissingBean} pouzdano vidi bean
 * prijave studenata i tada {@link NemaIdentiteta} ne nastaje. Kao obična {@code @Configuration} uslov bi zavisio od
 * redosleda skeniranja.
 */
@AutoConfiguration
public class IdentitetAutoKonfig {

    @Bean
    @ConditionalOnMissingBean(IdentitetStudenta.class)
    IdentitetStudenta nemaIdentiteta() {
        return new NemaIdentiteta();
    }
}
