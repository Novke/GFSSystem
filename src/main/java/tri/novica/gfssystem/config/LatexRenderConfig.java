package tri.novica.gfssystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * {@code RestClient.Builder} za render servis (kontejner {@code gfs-latex}). Pom nema {@code spring-boot-restclient}, pa
 * Boot ne registruje svoj builder; ovaj je prototip kao i njegov, sa vezom 5 s i čitanjem {@code gfs.latex.timeout-sekunde}.
 * HTTP/1.1 jer {@code HttpClient} na običnom {@code http://} inače šalje {@code Upgrade: h2c}, što servis ne traži.
 */
@Configuration
public class LatexRenderConfig {

    private static final Duration VEZA = Duration.ofSeconds(5);

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder latexRestClientBuilder(@Value("${gfs.latex.timeout-sekunde}") int timeoutSekunde) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(VEZA).build();
        JdkClientHttpRequestFactory fabrika = new JdkClientHttpRequestFactory(http);
        fabrika.setReadTimeout(Duration.ofSeconds(timeoutSekunde));
        return RestClient.builder().requestFactory(fabrika);
    }
}
