package tri.novica.gfssystem.service.raspored;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlPosleCommita;
import tri.novica.gfssystem.service.mejl.MejlServis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link ObavestavanjeStudenata} mejlom: jedna poruka po adresi (adrese se porede bez razlike velikih i malih slova, bez
 * CC/BCC listi), predata posle commit-a zasebnom izvršiocu {@code mejlStudentimaIzvrsilac} (jedna nit, red od 500), da
 * kodovi za prijavu na {@code mejlIzvrsilac} nikad ne čekaju iza talasa otkazivanja. Pun red: poruka se ne šalje i broji
 * se kao {@code neposlato}; bez SMTP-a ništa se ne šalje i svi primaoci su {@code neposlato}. Log: samo vrsta, id stavke
 * i brojevi (nikad adresa ni ime).
 */
@Component
@Slf4j
public class MejlObavestavanjeStudenata implements ObavestavanjeStudenata {

    private final MejlServis mejl;
    private final MejlPoruke poruke;
    private final MejlPosleCommita slanje;
    private final TaskExecutor izvrsilac;

    public MejlObavestavanjeStudenata(MejlServis mejl, MejlPoruke poruke, MejlPosleCommita slanje,
                                      @Qualifier("mejlStudentimaIzvrsilac") TaskExecutor izvrsilac) {
        this.mejl = mejl;
        this.poruke = poruke;
        this.slanje = slanje;
        this.izvrsilac = izvrsilac;
    }

    @Override
    public Ishod obavesti(List<Student> studenti, PromenaTermina promena) {
        Map<String, Student> poAdresi = new LinkedHashMap<>();
        for (Student s : studenti) {
            String adresa = s.getEmail() == null ? "" : s.getEmail().strip();
            if (!adresa.isEmpty()) {
                poAdresi.putIfAbsent(adresa.toLowerCase(Locale.ROOT), s);
            }
        }
        if (poAdresi.isEmpty()) {
            return Ishod.NISTA;
        }
        if (!mejl.dostupan()) {
            log.warn("Studenti nisu obavešteni mejlom (slanje nije podešeno), vrsta={}, stavka={}, primalaca={}",
                    promena.vrsta(), promena.termin().stavkaId(), poAdresi.size());
            return new Ishod(0, poAdresi.size());
        }
        int poslato = 0;
        int neposlato = 0;
        for (Student s : poAdresi.values()) {
            MejlPoruka poruka = poruke.terminStudentu(s.getEmail().strip(), s.getIme(), promena);
            try {
                izvrsilac.execute(() -> slanje.posaljiSaPokusajima(poruka));
                poslato++;
            } catch (TaskRejectedException e) {
                neposlato++;
            }
        }
        if (neposlato > 0) {
            log.warn("Red mejlova studentima je pun, vrsta={}, stavka={}, datum={}, u redu={}, nije poslato={}",
                    promena.vrsta(), promena.termin().stavkaId(), promena.termin().datum(), poslato, neposlato);
        } else {
            log.info("Studenti obavešteni mejlom, vrsta={}, stavka={}, datum={}, primalaca={}", promena.vrsta(),
                    promena.termin().stavkaId(), promena.termin().datum(), poslato);
        }
        return new Ishod(poslato, neposlato);
    }
}
