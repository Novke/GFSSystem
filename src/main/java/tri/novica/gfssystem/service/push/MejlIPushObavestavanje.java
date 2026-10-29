package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;
import tri.novica.gfssystem.service.raspored.MejlObavestavanjeStudenata;
import tri.novica.gfssystem.service.raspored.ObavestavanjeStudenata;
import tri.novica.gfssystem.service.raspored.PromenaTermina;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link ObavestavanjeStudenata} mejlom i push-om (spec aplikacije 6.2): isti primaoci, svaki kanal po svom prekidaču
 * (mejl: {@code mejl_otkazan_cas}; push: {@code push_otkazan_cas}). Push ide iz redova zvona upisanih u transakciji promene
 * (ključ {@link PromenaTermina#kljuc()}), sa {@code Urgency: high} i TTL-om do početka časa (najmanje 60 s). Postojeća polja
 * ishoda zadržavaju značenje "mejl".
 */
@Component
@Primary
@Slf4j
public class MejlIPushObavestavanje implements ObavestavanjeStudenata {

    static final Duration NAJMANJI_TTL = Duration.ofSeconds(60);

    private final MejlObavestavanjeStudenata mejl;
    private final StudentskaObavestenja obavestenja;
    private final Clock clock;

    public MejlIPushObavestavanje(MejlObavestavanjeStudenata mejl, StudentskaObavestenja obavestenja, Clock clock) {
        this.mejl = mejl;
        this.obavestenja = obavestenja;
        this.clock = clock;
    }

    @Override
    public Ishod obavesti(List<Student> studenti, PromenaTermina promena) {
        Ishod m = mejl.obavesti(studenti, promena);
        int push = 0;
        try {
            push = obavestenja.posalji(promena.kljuc(), studenti.stream().map(Student::getId).toList(),
                    Prekidac.PUSH_OTKAZAN_CAS, ttl(promena), true);
        } catch (RuntimeException e) {
            log.error("Push o promeni termina nije stavljen u red, stavka={}: {}", promena.termin().stavkaId(),
                    e.getClass().getSimpleName());
        }
        if (push > 0) {
            log.info("Studenti obavešteni push-om, vrsta={}, stavka={}, datum={}, poruka={}", promena.vrsta(),
                    promena.termin().stavkaId(), promena.termin().datum(), push);
        }
        return new Ishod(m.poslato(), m.neposlato(), push);
    }

    private Duration ttl(PromenaTermina p) {
        Duration d = Duration.between(LocalDateTime.now(clock), p.termin().datum().atTime(p.termin().pocetak()));
        return d.compareTo(NAJMANJI_TTL) < 0 ? NAJMANJI_TTL : d;
    }
}
