package tri.novica.gfssystem.service.fajlovi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;
import tri.novica.gfssystem.repository.fajlovi.FajlPrezentacijeRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.service.portal.KontekstStudenta;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Jedino mesto pravila "ko sme da preuzme fajl uz prezentaciju" (spec fajlova, odeljak 4); REST slojevi ne dupliraju uslove.
 * <ul>
 * <li><b>Čas (kod):</b> fajlovi koda su fajlovi prezentacija izvođenih na predavanju koda (odnosno tog jednog izvođenja)
 * koji su bez slajda ili im je slajd prikazan nekom od tih izvođenja ({@code slajdovi_izvodjenja}). Redosled: prezentacija po
 * prvom početku izvođenja, pa {@code rb}, pa id. Važenje samog koda proverava {@link KodoviFajlova#vazeci}.</li>
 * <li><b>Nalog:</b> student sme sve fajlove prezentacije P (predmet X) kad postoji završeno izvođenje P sa grupom iz
 * {@link Kontekst#grupe(Long)} (sopstvena + dodatne po predmetu, kao portal) i X je među {@code predmeti(k)}.
 * Strano ili nepostojeće se ovde vidi kao "ne sme" / prazan spisak; 404 baca pozivalac.</li>
 * <li><b>Mejl:</b> pravilo naloga ili {@link #naVazecemKodu}.</li>
 * </ul>
 * Vraćeni entiteti imaju inicijalizovane veze (prezentacija, slajd, medij), pa ih {@code FajlMapper} čita i bez sesije.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PristupFajlovima {

    private final FajlPrezentacijeRepository fajlovi;
    private final PrezentacijaRepository prezentacijaRepository;
    private final KontekstStudenta kontekstStudenta;
    private final Clock clock;

    /** Fajlovi važećeg koda, redom iz spec-a; pozivalac je prethodno dobio kod iz {@link KodoviFajlova#vazeci}. */
    public List<FajlPrezentacije> fajloviKoda(KodFajlova k) {
        if (k.getPredavanje() != null) {
            return inicijalizuj(fajlovi.zaPredavanje(k.getPredavanje().getId()));
        }
        if (k.getIzvodjenje() != null) {
            return inicijalizuj(fajlovi.zaIzvodjenje(k.getIzvodjenje().getId()));
        }
        return List.of();
    }

    /** Da li je fajl trenutno na spisku nekog važećeg koda (uživo na času). */
    public boolean naVazecemKodu(long fajlId) {
        LocalDateTime sada = LocalDateTime.now(clock);
        return fajlovi.naKoduPredavanja(fajlId, sada) > 0 || fajlovi.naKoduIzvodjenja(fajlId, sada) > 0;
    }

    /** Pravilo naloga za jedan fajl. */
    public boolean smeNalog(long studentId, FajlPrezentacije f) {
        return nalogOtvaraPrezentaciju(studentId, f.getPrezentacija().getId());
    }

    /** Fajlovi svih prezentacija predmeta koje nalog otvara, od najnovije održane; prazno kad student ne sluša predmet. */
    public List<FajlPrezentacije> zaNalog(long studentId, Long predmetId) {
        Set<Long> grupe = grupeZaPredmet(studentId, predmetId);
        return grupe.isEmpty() ? List.of() : inicijalizuj(fajlovi.zaNalog(predmetId, grupe));
    }

    /** Svi fajlovi jedne prezentacije kad pravilo naloga važi (redom {@code rb}, id), inače prazno. */
    public List<FajlPrezentacije> zaNalogPrezentacije(long studentId, Long prezentacijaId) {
        if (!nalogOtvaraPrezentaciju(studentId, prezentacijaId)) {
            return List.of();
        }
        return inicijalizuj(fajlovi.findAllByPrezentacijaIdOrderByRbAscIdAsc(prezentacijaId));
    }

    /** Prezentacije predmeta sa fajlom koje nalog otvara: id -> kad je održana, od najnovije (pomoć za {@code FajloviPrezentacije}). */
    public LinkedHashMap<Long, LocalDateTime> odrzaneZaNalog(long studentId, Long predmetId) {
        LinkedHashMap<Long, LocalDateTime> rez = new LinkedHashMap<>();
        Set<Long> grupe = grupeZaPredmet(studentId, predmetId);
        if (!grupe.isEmpty()) {
            for (FajlPrezentacijeRepository.Odrzana o : fajlovi.odrzane(predmetId, grupe)) {
                rez.put(o.getPrezentacijaId(), o.getOdrzana());
            }
        }
        return rez;
    }

    private boolean nalogOtvaraPrezentaciju(long studentId, Long prezentacijaId) {
        if (prezentacijaId == null) return false;
        Optional<Prezentacija> p = prezentacijaRepository.findById(prezentacijaId);
        if (p.isEmpty()) return false;
        Set<Long> grupe = grupeZaPredmet(studentId, p.get().getPredmet().getId());
        return !grupe.isEmpty() && fajlovi.zavrsenaZaGrupe(prezentacijaId, grupe) > 0;
    }

    /** Grupe studenta za predmet, prazno kad student predmet ne sluša (kao {@code KontekstStudenta.zaPredmet}, bez 404). */
    private Set<Long> grupeZaPredmet(long studentId, Long predmetId) {
        if (predmetId == null) return Set.of();
        Kontekst k = kontekstStudenta.kontekst(studentId);
        if (kontekstStudenta.predmeti(k).stream().noneMatch(p -> p.id().equals(predmetId))) return Set.of();
        return k.grupe(predmetId);
    }

    private static List<FajlPrezentacije> inicijalizuj(List<FajlPrezentacije> lista) {
        for (FajlPrezentacije f : lista) {
            f.getPrezentacija().getNaziv();
            if (f.getSlajd() != null) f.getSlajd().getRb();
            f.getMedij().getVelicina();
        }
        return lista;
    }
}
