package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Izvršilac i transport za push (spec aplikacije 5.3). {@code pushIzvrsilac}: dve niti i sopstveni ograničen red
 * ({@code gfs.push.izvrsilac.*}), odvojeno od mejl izvršilaca, da kodovi za prijavu nikad ne čekaju iza push-a. Poruke
 * se predaju direktno posle commit-a; pun red baca {@code TaskRejectedException} (podrazumevani {@code AbortPolicy}) i
 * pozivalac ga hvata i broji (poruka se tada propušta: zakasnelo obaveštenje je gore od izostalog).
 * <p>
 * Transport: {@link HttpPushTransport}; {@code gfs.push.http=false} (samo test resursi) postavlja transport koji ništa ne
 * šalje, da nijedan testni kontekst nikad ne ode na pravi push servis. Pravila adrese i šifrovanja važe isto u oba.
 */
@Configuration
@Slf4j
public class PushConfig {

    @Bean("pushIzvrsilac")
    public ThreadPoolTaskExecutor pushIzvrsilac(@Value("${gfs.push.izvrsilac.niti:2}") int niti,
                                                @Value("${gfs.push.izvrsilac.red:1000}") int red) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(niti);
        e.setMaxPoolSize(niti);
        e.setQueueCapacity(red);
        e.setThreadNamePrefix("push-");
        e.setWaitForTasksToCompleteOnShutdown(false);
        return e;
    }

    @Bean
    @ConditionalOnProperty(name = "gfs.push.http", havingValue = "true", matchIfMissing = true)
    public PushTransport httpPushTransport() {
        return new HttpPushTransport();
    }

    @Bean
    @ConditionalOnProperty(name = "gfs.push.http", havingValue = "false")
    public PushTransport bezSlanjaPushTransport() {
        log.warn("Push transport je isključen (gfs.push.http=false): poruke se ne šalju");
        return z -> PushTransport.PRESKOCENO;
    }
}
