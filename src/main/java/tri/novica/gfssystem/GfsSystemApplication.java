package tri.novica.gfssystem;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

import java.time.Clock;

// bez korisnika sa generisanom lozinkom: nastavnik i studenti se prijavljuju našim sesijama (security/SecurityConfig)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(NastavnikSvojstva.class)
public class GfsSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(GfsSystemApplication.class, args);
    }

    @Bean
    ModelMapper mapper(){
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setPropertyCondition(context -> context.getSource() != null)
                .setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        return mapper;
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

}
