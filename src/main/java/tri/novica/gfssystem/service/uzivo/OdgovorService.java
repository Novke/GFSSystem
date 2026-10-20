package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.OdgovorCmd;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.OdgovorRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.repository.uzivo.ZadatakDodelaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Prijem odgovora učesnika (spec 1.11, 4.5): zaključa red izvođenja (pa ide redom sa komandama i zatvaranjem po roku),
 * proveri izvođenje, učesnika, fazu i rundu, rok + 1 s i jedinstvenost, oceni odgovor iz snimka runde i upiše ga.
 * Svako odbijanje je {@link OdgovorOdbijen} sa porukom za učesnika. Ne menja verziju stanja (verzija raste po komandi).
 *
 * <p><b>Zadatak</b> ({@link ZadatakSnimak}): ocenjuje se po varijanti iz snimka runde koju učesnik ima u dodeli
 * ({@code zadatak_dodele}; bez dodele {@value #NIJE_DODELJEN}), polje po polje ({@link OcenaZadatka}), nikad po
 * trenutnoj banci: izmena ili arhiviranje varijante posle otvaranja ne menja ocenu. Upisuje se {@code polja} (JSON
 * {@code kljuc -> PoljeOdgovora}), {@code varijantaId}, {@code tacno}, poeni i vreme; ostala polja odgovora su
 * {@code null}. Za pitanje se {@code cmd.polja} ignoriše.
 *
 * <p><b>Vreme odgovora</b> ({@code vremeMs}, za poene i rang) ne računa pauze tajmera. Kad runda ima trajanje (tajmer
 * od otvaranja), {@code trajanjeMs - preostalo} je vreme koje je tajmer zaista radio: preostalo je {@code rok - sada}
 * dok tajmer radi, a {@code preostaloMs} dok je pauziran; ±10 s menja trajanje i preostalo za isto, pa razlika ostaje
 * tačna. Rezultat je u {@code [0, trajanjeMs]}. Bez trajanja (pitanje bez ograničenja, tajmer pokrenut naknadno, i
 * ponovo otvorena runda, kojoj O briše trajanje) vreme je {@code sada - otvoreno}; tada poeni ne zavise od brzine
 * (1000), a vreme služi samo za rang pri jednakim poenima.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OdgovorService {

    public static final String VEC_ODGOVORIO = "Već si odgovorio.";
    public static final String ZATVORENO = "Pitanje je zatvoreno.";
    public static final String ISTEKLO = "Vreme je isteklo.";
    public static final String NIJE_PRIJAVLJEN = "Nisi prijavljen na ovo izvođenje.";
    public static final String ZAVRSENO = IzvodjenjeService.ZAVRSENO;
    public static final String NIJE_DODELJEN = "Zadatak ti nije dodeljen.";

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final OdgovorRepository odgovorRepository;
    private final UcesnikRepository ucesnikRepository;
    private final ZadatakDodelaRepository dodelaRepository;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void odgovori(Long izvodjenjeId, Long ucesnikId, OdgovorCmd cmd) {
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(izvodjenjeId)
                .orElseThrow(() -> new OdgovorOdbijen(ZAVRSENO));
        if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) {
            throw new OdgovorOdbijen(ZAVRSENO);
        }
        Ucesnik u = (ucesnikId == null ? Optional.<Ucesnik>empty()
                : ucesnikRepository.findByIdAndIzvodjenjeId(ucesnikId, iz.getId()))
                .filter(x -> !x.isIzbacen())
                .orElseThrow(() -> new OdgovorOdbijen(NIJE_PRIJAVLJEN));

        Long rundaId = cmd == null ? null : cmd.rundaId();
        if (rundaId == null || iz.getFaza() != Faza.OTVORENO || !rundaId.equals(iz.getTrenutnaRundaId())) {
            throw new OdgovorOdbijen(ZATVORENO);
        }
        PitanjeRunda r = rundaRepository.findById(rundaId)
                .filter(x -> x.getZatvoreno() == null)
                .orElseThrow(() -> new OdgovorOdbijen(ZATVORENO));
        LocalDateTime sada = LocalDateTime.now(clock);
        if (r.getRok() != null && sada.isAfter(r.getRok().plus(IzvodjenjeService.TOLERANCIJA))) {
            throw new OdgovorOdbijen(ISTEKLO);
        }
        if (odgovorRepository.existsByRundaIdAndUcesnikId(r.getId(), u.getId())) {
            throw new OdgovorOdbijen(VEC_ODGOVORIO);
        }

        SnimakRunde snimakRunde = jsonMapper.readValue(r.getSnimak(), SnimakRunde.class);
        if (snimakRunde instanceof ZadatakSnimak zadatak) {
            upisiZadatak(iz, u, r, zadatak, cmd, sada);
            return;
        }
        PitanjeSnimak snimak = (PitanjeSnimak) snimakRunde;
        OdgovorVrednost v = vrednost(snimak.tip(), cmd);
        Boolean tacno;
        try {
            tacno = Ocenjivac.tacno(snimak, v);
        } catch (SystemException e) {
            throw new OdgovorOdbijen(e.getMessage());
        }
        long vremeMs = vremeMs(r, sada);

        Odgovor o = new Odgovor();
        o.setRunda(r);
        o.setUcesnik(u);
        o.setOpcije(v.opcije() == null ? null
                : v.opcije().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        o.setBroj(v.broj());
        o.setTekst(v.tekst());
        o.setSkala(v.skala());
        o.setTacno(tacno);
        o.setPoeni(Ocenjivac.poeni(tacno, iz.isTakmicenje(), r.getTrajanjeMs(), vremeMs));
        o.setVremeMs(vremeMs);
        o.setSakriven(false);
        o.setKreirano(sada);
        sacuvaj(iz, u, o);
    }

    /** Zadatak: dodela, varijanta iz snimka po dodeli, ocena po poljima, upis. */
    private void upisiZadatak(Izvodjenje iz, Ucesnik u, PitanjeRunda r, ZadatakSnimak snimak, OdgovorCmd cmd,
                              LocalDateTime sada) {
        Long varijantaId = dodelaRepository.findByRundaIdAndUcesnikId(r.getId(), u.getId())
                .map(d -> d.getVarijanta().getId())
                .orElseThrow(() -> new OdgovorOdbijen(NIJE_DODELJEN));
        VarijantaSnimak v = snimak.varijanta(varijantaId).orElseThrow(() -> new OdgovorOdbijen(NIJE_DODELJEN));
        OcenaZadatka.Ishod ishod;
        try {
            ishod = OcenaZadatka.oceni(v, cmd.polja());
        } catch (SystemException e) {
            throw new OdgovorOdbijen(e.getMessage());
        }
        long vremeMs = vremeMs(r, sada);

        Odgovor o = new Odgovor();
        o.setRunda(r);
        o.setUcesnik(u);
        o.setPolja(jsonMapper.writeValueAsString(ishod.polja()));
        o.setVarijantaId(varijantaId);
        o.setTacno(ishod.tacno());
        o.setPoeni(Ocenjivac.poeni(ishod.tacno(), iz.isTakmicenje(), r.getTrajanjeMs(), vremeMs));
        o.setVremeMs(vremeMs);
        o.setSakriven(false);
        o.setKreirano(sada);
        sacuvaj(iz, u, o);
    }

    private void sacuvaj(Izvodjenje iz, Ucesnik u, Odgovor o) {
        try {
            // IDENTITY: insert ide odmah, pa UNIQUE(runda_id, ucesnik_id) puca ovde (poslednja brana)
            odgovorRepository.save(o);
        } catch (DataIntegrityViolationException e) {
            throw new OdgovorOdbijen(VEC_ODGOVORIO);
        }
        publisher.publishEvent(new OdgovorPrimljen(iz.getId(), u.getId()));
    }

    /** Samo polje koje odgovara tipu pitanja (ostala se ignorišu); opcije bez duplikata, broj sa zarezom, tekst trimovan. */
    private static OdgovorVrednost vrednost(TipPitanja tip, OdgovorCmd cmd) {
        return switch (tip) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO -> {
                // null id ostaje u listi da ga Ocenjivac odbije ("Nepoznat odgovor."); sortira se tek posle provere
                List<Long> ids = cmd.opcije() == null ? null : cmd.opcije().stream().distinct().toList();
                yield new OdgovorVrednost(ids, null, null, null);
            }
            case BROJ -> new OdgovorVrednost(null, Normalizacija.broj(cmd.broj()), null, null);
            case KRATAK_TEKST -> new OdgovorVrednost(null, null, cmd.tekst() == null ? null : cmd.tekst().trim(), null);
            case SKALA -> new OdgovorVrednost(null, null, null, cmd.skala());
        };
    }

    /** Vreme koje je tajmer runde radio do odgovora (bez pauza), ili od otvaranja kad runda nema trajanje. */
    static long vremeMs(PitanjeRunda r, LocalDateTime sada) {
        Long trajanje = r.getTrajanjeMs();
        if (trajanje == null || trajanje <= 0) {
            return Math.max(0, Duration.between(r.getOtvoreno(), sada).toMillis());
        }
        long preostalo;
        if (r.getRok() != null) {
            preostalo = Duration.between(sada, r.getRok()).toMillis();
        } else if (r.getPreostaloMs() != null) {
            preostalo = r.getPreostaloMs();
        } else {
            preostalo = 0;   // trajanje bez roka i bez pauze se ne dešava (O briše trajanje); odbrambeno: najmanje poena
        }
        return Math.min(Math.max(trajanje - preostalo, 0), trajanje);
    }
}
