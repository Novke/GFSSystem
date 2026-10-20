package tri.novica.gfssystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Bez naloga (build bez prijave studenata) ostaje {@link NemaIdentiteta}: niko nije prijavljen, "Boduje se" nije dostupno. */
class IdentitetConfigTest {

    @Test
    void bezNalogaJeNemaIdentiteta() {
        new ApplicationContextRunner().withUserConfiguration(IdentitetConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(IdentitetStudenta.class);
            IdentitetStudenta i = ctx.getBean(IdentitetStudenta.class);
            assertThat(i).isInstanceOf(NemaIdentiteta.class);
            assertThat(i.dostupno()).isFalse();
            assertThat(i.studentId(new MockHttpServletRequest())).isEqualTo(Optional.empty());
        });
    }

    @Test
    void postojeciBeanImaPrednost() {
        IdentitetStudenta drugi = new IdentitetStudenta() {
            @Override public Optional<Long> studentId(jakarta.servlet.http.HttpServletRequest request) { return Optional.of(7L); }
            @Override public boolean dostupno() { return true; }
        };
        new ApplicationContextRunner()
                .withBean("drugi", IdentitetStudenta.class, () -> drugi)
                .withUserConfiguration(IdentitetConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IdentitetStudenta.class);
                    assertThat(ctx.getBean(IdentitetStudenta.class)).isSameAs(drugi);
                });
    }
}
