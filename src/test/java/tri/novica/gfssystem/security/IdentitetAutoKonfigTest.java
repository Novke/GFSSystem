package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IdentitetAutoKonfig}: bez korisničkog beana jedini {@link IdentitetStudenta} je {@link NemaIdentiteta}; bean
 * agenta prijave (korisnički, obrađen pre auto-konfiguracije) ga potiskuje.
 */
class IdentitetAutoKonfigTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentitetAutoKonfig.class));

    @Test
    void bezKorisnickogBeanaNemaIdentiteta() {
        runner.run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(IdentitetStudenta.class).size());
            IdentitetStudenta identitet = ctx.getBean(IdentitetStudenta.class);
            assertInstanceOf(NemaIdentiteta.class, identitet);
            assertFalse(identitet.dostupno());
            assertEquals(Optional.empty(), identitet.studentId(new MockHttpServletRequest()));
        });
    }

    @Test
    void korisnickiBeanPotiskujeNemaIdentiteta() {
        IdentitetStudenta lazni = new IdentitetStudenta() {
            @Override
            public Optional<Long> studentId(HttpServletRequest request) {
                return Optional.of(7L);
            }

            @Override
            public boolean dostupno() {
                return true;
            }
        };
        runner.withBean(IdentitetStudenta.class, () -> lazni).run(ctx -> {
            var beanovi = ctx.getBeansOfType(IdentitetStudenta.class);
            assertEquals(1, beanovi.size());
            assertSame(lazni, beanovi.values().iterator().next());
        });
    }
}
