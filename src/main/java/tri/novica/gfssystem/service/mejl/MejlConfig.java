package tri.novica.gfssystem.service.mejl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Asinhrono slanje mejla. {@code @EnableAsync} je u aplikaciji nov (do sada nigde nije bilo {@code @Async}); jedini
 * {@code @Async} je {@link MejlPosleCommita} sa imenovanim izvršiocem, pa ništa drugo ne dobija podrazumevani izvršilac.
 */
@Configuration
@EnableAsync
@Slf4j
public class MejlConfig {

    /**
     * Dve niti, red od 200 poruka (broj niti i veličina reda se čitaju iz {@code gfs.mail.izvrsilac.*} samo radi testa).
     * Pun red ne sme da baci izuzetak: {@code @Async} šalje u izvršilac iz {@code afterCommit} povratnog poziva i izuzetak
     * odatle stiže do pozivaoca {@code commit()}, tj. do zahteva, iako je transakcija već potvrđena. Zato se odbačena
     * poruka samo zabeleži (bez adrese, naslova i tela) i odbacuje; namerno nije {@code CallerRunsPolicy}, koja bi
     * SMTP pozvala u niti zahteva.
     */
    @Bean("mejlIzvrsilac")
    public ThreadPoolTaskExecutor mejlIzvrsilac(@Value("${gfs.mail.izvrsilac.niti:2}") int niti,
                                                @Value("${gfs.mail.izvrsilac.red:200}") int red) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(niti);
        e.setMaxPoolSize(niti);
        e.setQueueCapacity(red);
        e.setThreadNamePrefix("mejl-");
        e.setWaitForTasksToCompleteOnShutdown(false);
        e.setRejectedExecutionHandler((zadatak, izvrsilac) ->
                log.warn("Mejl nije poslat: red za slanje je pun, poruka je odbačena"));
        return e;
    }

    /**
     * Spring Boot ne uključuje {@code mail.smtp.auth} sam od sebe: bez toga se korisnik i lozinka ne šalju (Gmail).
     * Uključuje se samo kad je korisnik podešen, pa Mailpit bez prijave radi sa praznim {@code GFS_MAIL_USER}.
     */
    @Bean
    public SmartInitializingSingleton smtpPrijava(ObjectProvider<JavaMailSender> sender,
                                                  @Value("${spring.mail.username:}") String korisnik) {
        return () -> {
            JavaMailSender s = sender.getIfAvailable();
            if (s instanceof JavaMailSenderImpl impl) {
                impl.getJavaMailProperties().put("mail.smtp.auth", Boolean.toString(!korisnik.isBlank()));
            }
        };
    }
}
