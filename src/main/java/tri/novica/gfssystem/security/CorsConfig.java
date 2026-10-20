package tri.novica.gfssystem.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.util.Arrays;
import java.util.List;

/**
 * CORS za front iz {@code gfs.front.url} (zarez, razmaci se odsecaju). Iza dva nginx-a backend vidi običan {@code http},
 * pa je svaki upis iz pregledača CORS zahtev i origin stranice mora biti na listi. Ista podešavanja važe na dva mesta:
 * u bezbednosnom lancu ({@link #corsConfigurationSource()}, {@code http.cors}: preflight dobija CORS odgovor pre pravila
 * pristupa, tuđ origin 403) i u Spring MVC-u ({@link #addCorsMappings}, za testove bez lanca); lanac postavi zaglavlja
 * prvi, a MVC tada ništa ne dodaje. Ista lista je i za proveru porekla ({@link ProveraPoreklaFilter}).
 * <p>
 * LTI login i launch ({@link ProveraPoreklaFilter#LTI_IZUZECI}) su izuzeti na oba mesta ({@link #bezLti}): stižu kao
 * navigacija pregledača sa Moodle-a ({@code form_post} nosi {@code Origin} Moodle-a), pa bi ih CORS odbio sa 403; CORS
 * ovde ništa ne štiti (odgovor je preusmerenje, štite ga {@code state}, {@code nonce} i {@code gfs_lti}). Bez
 * konfiguracije ti odgovori nemaju ni CORS zaglavlja.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    static final String[] METODE = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    private final List<String> origini;

    public CorsConfig(@Value("${gfs.front.url}") String frontUrl) {
        this.origini = origini(frontUrl);
    }

    /** Origini iz {@code gfs.front.url}: zarez, razmaci se odsecaju, prazni se preskaču. */
    public static List<String> origini(String frontUrl) {
        return Arrays.stream(frontUrl.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(origini.toArray(String[]::new))
                .allowedMethods(METODE)
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /** Ime je bitno: i Springov {@code HandlerMappingIntrospector} je {@code CorsConfigurationSource}; lanac traži ovaj. */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origini);
        c.setAllowedMethods(List.of(METODE));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        // isto kao CorsRegistration iznad (Max-Age 1800 s za preflight)
        c.applyPermitDefaultValues();
        UrlBasedCorsConfigurationSource izvor = new UrlBasedCorsConfigurationSource();
        izvor.registerCorsConfiguration("/**", c);
        return bezLti(izvor);
    }

    /** Isti izuzetak za Spring MVC: svaki handler mapping dobija izvor bez LTI login-a i launch-a. */
    @Bean
    static BeanPostProcessor corsBezLtiUMvc() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String ime) {
                if (bean instanceof AbstractHandlerMapping m && m.getCorsConfigurationSource() != null) {
                    m.setCorsConfigurationSource(bezLti(m.getCorsConfigurationSource()));
                }
                return bean;
            }
        };
    }

    /** Izvor koji za LTI login i launch nema konfiguraciju ({@code null}: CORS procesor tada ne radi ništa). */
    static CorsConfigurationSource bezLti(CorsConfigurationSource izvor) {
        return req -> ProveraPoreklaFilter.LTI_IZUZECI.matches(req) ? null : izvor.getCorsConfiguration(req);
    }
}
