package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.test.online.StanjeOnline;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.entity.test.StanjePrimerka;
import tri.novica.gfssystem.entity.test.TestPrimerak;
import tri.novica.gfssystem.entity.test.TestPrimerakZadatak;
import tri.novica.gfssystem.entity.test.TestSmena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.test.TestPrimerakRepository;
import tri.novica.gfssystem.repository.test.TestPrimerakZadatakRepository;
import tri.novica.gfssystem.repository.test.TestSmenaRepository;

import java.util.*;

/**
 * Pravila online testa koja važe u postojećem {@code TestService}-u (plan 4.1): stanje za listu i detalje, zabrana
 * endpointa testa na papiru, brisanje i „Završi evidentiranje“.
 */
@Component
@RequiredArgsConstructor
public class OnlineTestPravila {

    public static final String SAMO_PAPIR = "Online test se menja kroz online tok.";
    public static final String U_TOKU = "Test je u toku; prvo ga završi.";
    public static final String NIJE_ZAVRSEN = "Test još nije završen.";
    public static final String RUCNI_BEZ_POENA = "Upiši poene za ručne zadatke.";

    private final TestSmenaRepository smenaRepository;
    private final TestPrimerakRepository primerakRepository;
    private final TestPrimerakZadatakRepository zadatakRepository;

    /** Stanje online testova po id-ju (testovi na papiru se preskaču). */
    public Map<Long, StanjeOnline> stanja(Collection<Long> testIds) {
        Map<Long, StanjeOnline> m = new HashMap<>();
        if (testIds.isEmpty()) return m;
        for (TestSmena s : smenaRepository.findByTestIdIn(testIds)) {
            if (s.getRb() == 1 || !m.containsKey(s.getTestId())) m.put(s.getTestId(), StanjeTesta.stanje(s));
        }
        return m;
    }

    public StanjeOnline stanje(Test t) {
        if (!t.isOnline()) return null;
        return smenaRepository.findFirstByTestIdOrderByRb(t.getId()).map(StanjeTesta::stanje).orElse(StanjeOnline.PRIPREMA);
    }

    /** Endpointi testa na papiru (ispitanici, evidentiranje, izmena) za online test: 409. */
    public void samoPapir(Test t) {
        if (t.isOnline()) throw new SystemException(SAMO_PAPIR, HttpStatus.CONFLICT);
    }

    /**
     * Online test se ne briše dok je ulaz otvoren ili je test u toku. U pripremi i posle završetka se briše sa primercima i
     * polaganjima, kao test na papiru sa svojim polaganjima (probni test, pogrešno napravljen rok); ekran traži potvrdu.
     */
    public void proveriBrisanje(Test t) {
        if (!t.isOnline()) return;
        StanjeOnline s = stanje(t);
        if (s == StanjeOnline.ULAZ || s == StanjeOnline.U_TOKU) throw new SystemException(U_TOKU, HttpStatus.CONFLICT);
    }

    /** „Završi evidentiranje“ online testa: tek kad je završen i kad svaki ručni zadatak predatih radova ima poene. */
    public void proveriZavrsiEvidentiranje(Test t) {
        if (!t.isOnline()) return;
        if (stanje(t) != StanjeOnline.ZAVRSEN) throw new SystemException(NIJE_ZAVRSEN, HttpStatus.CONFLICT);
        List<Long> predati = primerakRepository.zaTest(t.getId()).stream()
                .filter(p -> p.getStanje() == StanjePrimerka.PREDAT).map(TestPrimerak::getId).toList();
        if (predati.isEmpty()) return;
        for (TestPrimerakZadatak z : zadatakRepository.zaPrimerke(predati)) {
            if (z.getRucniPoeni() == null && TestJson.snimak(z.getSnimak()).rucno()) {
                throw new SystemException(RUCNI_BEZ_POENA, HttpStatus.CONFLICT);
            }
        }
    }
}
