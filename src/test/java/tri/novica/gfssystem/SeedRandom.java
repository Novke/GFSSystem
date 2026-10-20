package tri.novica.gfssystem;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Random;

/**
 * Zamenjuje {@code GfsSystemApplication.random()} seed-ovanim generatorom, da izvlačenje i raspodela zadataka u
 * testovima budu ponovljivi: {@code @Import(SeedRandom.class)} na testu. Seed je isti za svaki Spring kontekst, ali
 * generator se deli unutar jednog konteksta, pa test ne sme da pretpostavi koji će broj dobiti.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SeedRandom {

    @Bean
    @Primary
    Random testRandom() {
        return new Random(42);
    }
}
