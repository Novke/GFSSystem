package tri.novica.gfssystem.service.raspored;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlServis;

import java.util.List;
import java.util.Set;

/**
 * {@link ObavestavanjeStudenata} mejlom: jedan mejl po studentu koji ima adresu i nije na tabu "Ja" isključio "Javi mi
 * mejlom kad je čas otkazan" ({@link PodesavanjaStudentaServis}; važi i za "čas se ipak održava"), bez CC/BCC listi,
 * objavljen kao {@code MejlPoruka} i poslat posle commit-a, asinhrono ({@code MejlPosleCommita}, red od 200 poruka). Bez
 * SMTP-a ne šalje ništa i vraća 0. Log: samo vrsta, id stavke i broj primalaca (nikad adresa ni ime).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MejlObavestavanjeStudenata implements ObavestavanjeStudenata {

    private final MejlServis mejl;
    private final MejlPoruke poruke;
    private final ApplicationEventPublisher dogadjaji;
    private final PodesavanjaStudentaServis podesavanja;

    @Override
    public int obavesti(List<Student> studenti, PromenaTermina promena) {
        List<Student> saAdresom = studenti.stream().filter(s -> s.getEmail() != null && !s.getEmail().isBlank()).toList();
        if (saAdresom.isEmpty()) {
            return 0;
        }
        Set<Long> neZele = podesavanja.bezMejlaOtkazanogCasa(saAdresom.stream().map(Student::getId).toList());
        List<Student> saMejlom = saAdresom.stream().filter(s -> !neZele.contains(s.getId())).toList();
        if (saMejlom.isEmpty()) {
            return 0;
        }
        if (!mejl.dostupan()) {
            log.warn("Studenti nisu obavešteni mejlom (slanje nije podešeno), vrsta={}, stavka={}, primalaca={}",
                    promena.vrsta(), promena.termin().stavkaId(), saMejlom.size());
            return 0;
        }
        for (Student s : saMejlom) {
            dogadjaji.publishEvent(poruke.terminStudentu(s.getEmail().strip(), s.getIme(), promena));
        }
        log.info("Studenti obavešteni mejlom, vrsta={}, stavka={}, datum={}, primalaca={}", promena.vrsta(),
                promena.termin().stavkaId(), promena.termin().datum(), saMejlom.size());
        return saMejlom.size();
    }
}
