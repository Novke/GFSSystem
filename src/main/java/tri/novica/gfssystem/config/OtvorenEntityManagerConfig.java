package tri.novica.gfssystem.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Open-in-view kao ranije (Boot podrazumevano), osim za javni nalog studenta i LTI. Boot-ov presretač je isključen
 * ({@code spring.jpa.open-in-view=false}), a ovaj ga zamenjuje istim ponašanjem za sve ostale putanje, pa nastavnički
 * endpointi i dalje lenjo čitaju veze posle servisa.
 *
 * <p>{@code /public/nalog/**} i {@code /public/lti/**} su javni na internetu: entity manager otvoren za ceo zahtev drži
 * vezu iz pula od prvog upita do kraja odgovora, pa i dok {@code KodServis} spava do donje granice trajanja. Navala
 * neprijavljenih zahteva bi tako zauzela ceo pul (Hikari 10) i oborila aplikaciju. Bez open-in-view svaka transakcija
 * vraća vezu odmah po kraju; ti endpointi zato vraćaju samo DTO-ove napravljene u servisu.
 */
@Configuration
public class OtvorenEntityManagerConfig implements WebMvcConfigurer {

    /** Putanje bez open-in-view (u odnosu na kontekst; nginx ih vidi kao {@code /api/public/...}). */
    public static final String[] BEZ_OTVORENOG_EM = {"/public/nalog/**", "/public/lti/**"};

    private final ObjectProvider<EntityManagerFactory> emf;

    public OtvorenEntityManagerConfig(ObjectProvider<EntityManagerFactory> emf) {
        this.emf = emf;
    }

    /**
     * Bez bean-a: {@code @WebMvcTest} isečci učitavaju {@code WebMvcConfigurer}-e, ali nemaju JPA, pa se presretač
     * registruje samo kad EMF postoji (kao Boot-ov, koji je uslovljen JPA-om).
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        emf.ifAvailable(f -> {
            OpenEntityManagerInViewInterceptor osiv = new OpenEntityManagerInViewInterceptor();
            osiv.setEntityManagerFactory(f);
            registry.addWebRequestInterceptor(osiv).excludePathPatterns(BEZ_OTVORENOG_EM);
        });
    }
}
