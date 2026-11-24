package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Crtanje u izvođenju i tabla predavanja (spec {@code 2026-11-23-crtanje-uzivo-design.md} u GFS-deploy, ugovor u planu).
 * Strana izvođenja je (slajd, pozadina); upis zamenjuje ceo niz elemenata i podiže verziju strane, pod bravom reda izvođenja
 * (isti red kao komande), a posle commit-a ide samo snimak nastavnika ({@link CrtezPromenjen}). Crteži preživljavaju
 * "Ne čuvaj"; brišu se sa izvođenjem (FK CASCADE).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrtanjeService {

    static final int RANIJE_PO_POZADINI = 5;
    static final String SLAJD_NIJE_IZ_PREZENTACIJE = "Slajd nije iz prezentacije ovog izvođenja.";
    static final String PREDAVANJE_NIJE_PRONADJENO = "Predavanje nije pronađeno.";
    private static final String PRAZNO = "[]";

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final SlajdRepository slajdRepository;
    private final CrtezIzvodjenjaRepository crtezRepository;
    private final TablaPredavanjaRepository tablaRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final CrtanjeJson json;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    // ---------------------------------------------------------------- strane izvođenja

    @Transactional(readOnly = true)
    public StranaCrteza strana(Long izvodjenjeId, Long slajdId, PozadinaCrteza pozadina) {
        Izvodjenje iz = izvodjenje(izvodjenjeId);
        proveriStranu(iz, slajdId, pozadina);
        return crtezRepository.findByIzvodjenjeIdAndSlajdIdAndPozadina(izvodjenjeId, slajdId, pozadina)
                .map(c -> new StranaCrteza(json.izKolone(c.getElementi()), c.getVerzija()))
                .orElseGet(() -> new StranaCrteza(List.of(), 0));
    }

    /** Ceo niz elemenata strane; 400 neispravno, 404 izvođenje ili slajd, 410 završeno izvođenje. */
    public VerzijaCrteza upisi(Long izvodjenjeId, Long slajdId, PozadinaCrteza pozadina, JsonNode telo) {
        String zapis = zapis(telo);
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(izvodjenjeId)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) {
            throw new SystemException(IzvodjenjeService.ZAVRSENO, HttpStatus.GONE);
        }
        proveriStranu(iz, slajdId, pozadina);
        CrtezIzvodjenja c = crtezRepository.findByIzvodjenjeIdAndSlajdIdAndPozadina(izvodjenjeId, slajdId, pozadina)
                .orElseGet(() -> {
                    CrtezIzvodjenja n = new CrtezIzvodjenja();
                    n.setIzvodjenjeId(izvodjenjeId);
                    n.setSlajdId(slajdId);
                    n.setPozadina(pozadina);
                    return n;
                });
        c.setElementi(zapis);
        c.setVerzija(c.getVerzija() + 1);
        c.setIzmenjeno(sada());
        crtezRepository.save(c);
        publisher.publishEvent(new CrtezPromenjen(izvodjenjeId));
        return new VerzijaCrteza(c.getVerzija());
    }

    /** Ne-prazne strane izvođenja za pregled: redom slajdova (obrisani na kraju), SLAJD pre BELA. */
    @Transactional(readOnly = true)
    public List<CrtezIzvodjenjaInfo> sve(Long izvodjenjeId) {
        Izvodjenje iz = izvodjenje(izvodjenjeId);
        Map<Long, Integer> broj = new HashMap<>();
        List<Slajd> sl = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(iz.getPrezentacija().getId());
        for (int i = 0; i < sl.size(); i++) {
            broj.put(sl.get(i).getId(), i + 1);
        }
        return crtezRepository.findNeprazneByIzvodjenjeId(izvodjenjeId).stream()
                .map(c -> new CrtezIzvodjenjaInfo(c.getSlajdId(), c.getSlajdId() == null ? null : broj.get(c.getSlajdId()),
                        c.getPozadina(), json.izKolone(c.getElementi()), c.getVerzija(), c.getIzmenjeno()))
                .sorted(Comparator.comparing(CrtezIzvodjenjaInfo::slajdBroj, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CrtezIzvodjenjaInfo::pozadina))
                .toList();
    }

    /**
     * Crteži istog slajda iz drugih izvođenja iste prezentacije (i bez čuvanja), ne-prazni, najnoviji prvi, najviše
     * {@value #RANIJE_PO_POZADINI} po pozadini.
     */
    @Transactional(readOnly = true)
    public List<RanijiCrtez> ranije(Long izvodjenjeId, Long slajdId) {
        Izvodjenje iz = izvodjenje(izvodjenjeId);
        proveriSlajd(iz, slajdId);
        Map<PozadinaCrteza, Integer> uzeto = new EnumMap<>(PozadinaCrteza.class);
        List<RanijiCrtez> rez = new ArrayList<>();
        for (RanijiCrtezRed r : crtezRepository.findRanije(slajdId, izvodjenjeId)) {
            int n = uzeto.merge(r.pozadina(), 1, Integer::sum);
            if (n > RANIJE_PO_POZADINI) continue;
            rez.add(new RanijiCrtez(r.izvodjenjeId(), r.pocetak(), r.grupa(), r.pozadina(), json.izKolone(r.elementi())));
        }
        return rez;
    }

    /**
     * Trenutna strana za snimak nastavnika (plan, "Ugovor"): beli ekran = bela strana slajda {@code belaSlajdId} (null dok
     * ga nema), slajd na platnu = njegova strana SLAJD (i na crnom ekranu), inače {@code null}.
     */
    @Transactional(readOnly = true)
    public CrtezTrenutni trenutni(Izvodjenje iz) {
        Long slajdId;
        PozadinaCrteza pozadina;
        if (iz.getEkran() == Ekran.BEO) {
            if (iz.getBelaSlajdId() == null) return null;
            slajdId = iz.getBelaSlajdId();
            pozadina = PozadinaCrteza.BELA;
        } else if (iz.getPrikaz() == Prikaz.SLAJD && iz.getTrenutniSlajdId() != null) {
            slajdId = iz.getTrenutniSlajdId();
            pozadina = PozadinaCrteza.SLAJD;
        } else {
            return null;
        }
        int verzija = crtezRepository.findVerzija(iz.getId(), slajdId, pozadina).orElse(0);
        return new CrtezTrenutni(slajdId, pozadina, verzija);
    }

    // ---------------------------------------------------------------- tabla predavanja

    /** Tabla predavanja; prazno kad je {@code posle} zadat, a verzija nije veća (REST tada vraća 204). */
    @Transactional(readOnly = true)
    public Optional<StranaCrteza> tabla(Long predavanjeId, Integer posle) {
        if (!predavanjeRepository.existsById(predavanjeId)) {
            throw new SystemException(PREDAVANJE_NIJE_PRONADJENO, HttpStatus.NOT_FOUND);
        }
        StranaCrteza s = tablaRepository.findByPredavanjeId(predavanjeId)
                .map(t -> new StranaCrteza(json.izKolone(t.getElementi()), t.getVerzija()))
                .orElseGet(() -> new StranaCrteza(List.of(), 0));
        return posle != null && s.verzija() <= posle ? Optional.empty() : Optional.of(s);
    }

    public VerzijaCrteza upisiTablu(Long predavanjeId, JsonNode telo) {
        String zapis = zapis(telo);
        predavanjeRepository.findByIdForUpdate(predavanjeId)
                .orElseThrow(() -> new SystemException(PREDAVANJE_NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        TablaPredavanja t = tablaRepository.findByPredavanjeId(predavanjeId).orElseGet(() -> {
            TablaPredavanja n = new TablaPredavanja();
            n.setPredavanjeId(predavanjeId);
            return n;
        });
        t.setElementi(zapis);
        t.setVerzija(t.getVerzija() + 1);
        t.setIzmenjeno(sada());
        tablaRepository.save(t);
        return new VerzijaCrteza(t.getVerzija());
    }

    // ---------------------------------------------------------------- pomoćno

    private String zapis(JsonNode telo) {
        List<ElementCrteza> el = CrtanjePravila.normalizuj(json.procitaj(telo).elementi());
        String zapis = el.isEmpty() ? PRAZNO : json.zapisi(el);
        CrtanjePravila.proveriVelicinu(zapis);
        return zapis;
    }

    private Izvodjenje izvodjenje(Long id) {
        return izvodjenjeRepository.findById(id)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
    }

    private void proveriStranu(Izvodjenje iz, Long slajdId, PozadinaCrteza pozadina) {
        if (pozadina == null) {
            throw new SystemException("Pozadina strane nedostaje (SLAJD ili BELA).", HttpStatus.BAD_REQUEST);
        }
        proveriSlajd(iz, slajdId);
    }

    private void proveriSlajd(Izvodjenje iz, Long slajdId) {
        if (slajdId == null || !slajdRepository.existsByIdAndPrezentacijaId(slajdId, iz.getPrezentacija().getId())) {
            throw new SystemException(SLAJD_NIJE_IZ_PREZENTACIJE, HttpStatus.NOT_FOUND);
        }
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }
}
