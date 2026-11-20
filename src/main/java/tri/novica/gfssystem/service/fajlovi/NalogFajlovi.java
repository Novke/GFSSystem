package tri.novica.gfssystem.service.fajlovi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.fajlovi.FajlKratko;
import tri.novica.gfssystem.dto.fajlovi.FajloviPrezentacije;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.repository.fajlovi.FajlPrezentacijeRepository;
import tri.novica.gfssystem.service.portal.KontekstStudenta;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Fajlovi uz prezentaciju na nalogu studenta (spec fajlova, odeljak 5): spisak po predmetu i pronalaženje fajla za
 * preuzimanje. Pravilo "ko sme" je isključivo {@link PristupFajlovima#smeNalog}; ovde nema sopstvenih uslova. Tuđe,
 * nepostojeće i fajl čiju prezentaciju nalog ne otvara (izvođenje u toku, tuđa grupa) su isti 404 {@code Nije pronađeno.}.
 * Fajl neprikazanog slajda JESTE u nalogu (pravilo slajdova se ovim ne menja, odeljak 10).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NalogFajlovi {

    private final PristupFajlovima pristup;
    private final KontekstStudenta kontekstStudenta;
    private final FajlPrezentacijeRepository fajlovi;

    /** Za preuzimanje: {@code naziv} je ime za {@code Content-Disposition}, {@code medijId} je ključ za {@code MedijService}. */
    public record ZaPreuzimanje(String naziv, String medijId) {
    }

    /** Prezentacije predmeta koje nalog otvara i imaju bar jedan fajl, od najnovije održane; 404 za predmet koji student ne sluša. */
    public List<FajloviPrezentacije> spisak(long studentId, Long predmetId) {
        kontekstStudenta.zaPredmet(studentId, predmetId);
        LinkedHashMap<Long, LocalDateTime> odrzane = pristup.odrzaneZaNalog(studentId, predmetId);
        Map<Long, List<FajlPrezentacije>> poPrezentaciji = new LinkedHashMap<>();
        for (FajlPrezentacije f : pristup.zaNalog(studentId, predmetId)) {
            poPrezentaciji.computeIfAbsent(f.getPrezentacija().getId(), k -> new ArrayList<>()).add(f);
        }
        List<FajloviPrezentacije> rez = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> e : odrzane.entrySet()) {
            List<FajlPrezentacije> lista = poPrezentaciji.get(e.getKey());
            if (lista == null || lista.isEmpty()) continue;
            List<FajlKratko> kratki = lista.stream().map(FajlMapper::kratko).toList();
            rez.add(new FajloviPrezentacije(e.getKey(), lista.get(0).getPrezentacija().getNaziv(), e.getValue(), kratki));
        }
        return rez;
    }

    /** Fajl koji student sme da preuzme po pravilu naloga; inače 404. */
    public ZaPreuzimanje zaPreuzimanje(long studentId, Long fajlId) {
        FajlPrezentacije f = fajlId == null ? null : fajlovi.findById(fajlId).orElse(null);
        if (f == null || !pristup.smeNalog(studentId, f)) throw KontekstStudenta.nema();
        return new ZaPreuzimanje(f.getNaziv(), f.getMedij().getId());
    }
}
