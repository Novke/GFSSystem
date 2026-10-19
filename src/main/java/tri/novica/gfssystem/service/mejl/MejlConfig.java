package tri.novica.gfssystem.service.mejl;

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
public class MejlConfig {

    /** Dve niti, red od 200 poruka; pun red baca izuzetak koji listener zabeleži (mejl se tada ne šalje). */
    @Bean("mejlIzvrsilac")
    public ThreadPoolTaskExecutor mejlIzvrsilac() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(200);
        e.setThreadNamePrefix("mejl-");
        e.setWaitForTasksToCompleteOnShutdown(false);
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
