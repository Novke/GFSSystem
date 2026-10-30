package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.portal.OtvorenoZaGrupu;
import tri.novica.gfssystem.entity.portal.IzvorOtvaranja;
import tri.novica.gfssystem.entity.portal.OtvorenaTema;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.portal.OtvorenaTemaRepository;
import tri.novica.gfssystem.repository.portal.PrikazaniSlajdoviRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.uzivo.SnimakRunde;
import tri.novica.gfssystem.service.uzivo.VarijantaSnimak;
import tri.novica.gfssystem.service.uzivo.ZadatakSnimak;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Šta je otvoreno za vežbanje i koji slajdovi su prikazani grupi (spec studentskog dela 5.3, 6.1, 12.3).
 * <ul>
 *   <li>{@link #krajIzvodjenja}: listovi izvučenih varijanti iz ZADATAK rundi izvođenja sa grupom postaju otvoreni (CAS).
 *       Zove ga {@code IzvodjenjeService.zavrsi} (komanda ZAVRSI i automatski kraj posle 12 h) u istoj transakciji,
 *       <b>pre</b> brisanja rundi kod "Ne čuvaj". Tek na kraju, nikad pri otvaranju runde (spec 5.3).</li>
 *   <li>{@link #prikazan}: konzola stala na slajd izvođenja sa grupom; red "ako ne postoji".</li>
 *   <li>Ručno otvaranje i zatvaranje po grupi studenata (nastavnik, panel "Za vežbanje" u banci).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TemeZaVezbanje {

    static final String NIJE_ZA_PREDAVANJA = "Tip nije u banci za predavanja.";
    static final String NEMA_GRUPE = "Grupa ne postoji.";
    /** Koliko posle kraja poslednjeg izvođenja sa grupom ide zbirno obaveštenje "posle časa". */
    public static final java.time.Duration POSLE_CASA_ZA = java.time.Duration.ofMinutes(30);

    private final OtvorenaTemaRepository otvoreneRepository;
    private final PrikazaniSlajdoviRepository prikazaniRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final GrupaZadatakaRepository grupaZadatakaRepository;
    private final KandidatiZadatka kandidati;
    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Kraj izvođenja: listovi varijanti izvučenih u njegovim ZADATAK rundama otvaraju se grupi izvođenja (izvor CAS, ako
     * zapis ne postoji). Izvođenje bez grupe ne otvara ništa. Neispravan snimak runde se preskače (zapis u log), pa kraj
     * izvođenja nikad ne pada zbog ovoga; list obrisan u međuvremenu se preskače u SQL-u.
     */
    public int krajIzvodjenja(Izvodjenje iz) {
        if (iz.getGrupa() == null) return 0;
        Long grupaId = iz.getGrupa().getId();
        zakaziPosleCasa(iz);
        Set<Long> listovi = new LinkedHashSet<>();
        for (PitanjeRunda r : rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(iz.getId())) {
            SnimakRunde sn;
            try {
                sn = jsonMapper.readValue(r.getSnimak(), SnimakRunde.class);
            } catch (RuntimeException e) {
                log.warn("Snimak runde nije čitljiv, preskačem otvaranje za vežbanje: runda={}", r.getId());
                continue;
            }
            if (sn instanceof ZadatakSnimak z && z.varijante() != null) {
                z.varijante().stream().map(VarijantaSnimak::grupaId).filter(Objects::nonNull).forEach(listovi::add);
            }
        }
        LocalDateTime sada = sada();
        int upisano = 0;
        for (Long list : listovi) {
            upisano += otvoreneRepository.otvoriAkoNema(grupaId, list, sada, IzvorOtvaranja.CAS.name(), iz.getId());
        }
        if (!listovi.isEmpty()) {
            log.info("Otvoreno za vežbanje posle izvođenja: izvodjenje={}, grupa={}, listova={}", iz.getId(), grupaId, listovi.size());
        }
        return upisano;
    }

    /**
     * "Posle časa" (spec aplikacije 6.4): slanje za grupu i dan izvođenja (datum početka) dospeva {@link #POSLE_CASA_ZA}
     * posle kraja; kraj kasnijeg izvođenja sa istom grupom istog dana ga pomera, a već obrađeno se ne dira. Šalje
     * {@code service.push.PosleCasaPosao}.
     */
    private void zakaziPosleCasa(Izvodjenje iz) {
        LocalDateTime kraj = iz.getKraj() == null ? sada() : iz.getKraj();
        LocalDateTime pocetak = iz.getPocetak() == null ? kraj : iz.getPocetak();
        jdbc.update("""
                INSERT INTO posle_casa_poslato (grupa_id, datum, dospeva) VALUES (?, ?, ?) AS novo
                ON DUPLICATE KEY UPDATE dospeva = IF(posle_casa_poslato.poslato IS NULL,
                    GREATEST(posle_casa_poslato.dospeva, novo.dospeva), posle_casa_poslato.dospeva)""",
                iz.getGrupa().getId(), pocetak.toLocalDate(), kraj.plus(POSLE_CASA_ZA));
    }

    /** Konzola stala na slajd izvođenja: red u {@code prikazani_slajdovi} "ako ne postoji" (samo izvođenje sa grupom). */
    public void prikazan(Izvodjenje iz, Long slajdId) {
        if (iz.getGrupa() == null || slajdId == null) return;
        prikazaniRepository.upisi(iz.getGrupa().getId(), slajdId, iz.getId(), sada());
    }

    // ---------------------------------------------------------------- nastavnik: panel "Za vežbanje"

    /** Stanje čvora po grupi studenata: grupe sa predavanjem iz predmeta čvora i grupe koje imaju zapis u stablu. */
    @Transactional(readOnly = true)
    public List<OtvorenoZaGrupu> otvoreno(Long temaId) {
        Cvor c = cvor(temaId);
        List<Long> preci = c.opis().putanja().stream().map(GrupaPutanja::id).filter(id -> !id.equals(temaId)).toList();
        List<Long> podstablo = c.stablo().podstablo(temaId);
        Set<Long> relevantni = new HashSet<>(preci);
        relevantni.addAll(podstablo);
        List<OtvorenaTema> zapisi = otvoreneRepository.findAllByTemaIdIn(relevantni);
        Map<Long, String> naziviPredaka = new HashMap<>();
        c.opis().putanja().forEach(p -> naziviPredaka.put(p.id(), p.naziv()));
        List<Long> listovi = c.opis().listovi();

        Set<Long> grupeSaZapisom = new HashSet<>();
        zapisi.forEach(z -> grupeSaZapisom.add(z.getGrupaId()));
        List<OtvorenoZaGrupu> rez = new ArrayList<>();
        for (Map<String, Object> g : grupe(c.predmetId(), grupeSaZapisom)) {
            Long grupaId = ((Number) g.get("id")).longValue();
            List<OtvorenaTema> svoji = zapisi.stream().filter(z -> z.getGrupaId().equals(grupaId)).toList();
            Optional<OtvorenaTema> direktno = svoji.stream().filter(z -> z.getTemaId().equals(temaId)).findFirst();
            Optional<OtvorenaTema> predak = svoji.stream().filter(z -> preci.contains(z.getTemaId())).findFirst();
            Set<Long> otvoreniListovi = new HashSet<>();
            if (direktno.isPresent() || predak.isPresent()) {
                otvoreniListovi.addAll(listovi);
            } else {
                for (OtvorenaTema z : svoji) {
                    Opis o = c.stablo().opis(z.getTemaId());
                    if (o != null) o.listovi().stream().filter(listovi::contains).forEach(otvoreniListovi::add);
                }
            }
            String stanje = direktno.isPresent() ? "OTVORENO" : predak.isPresent() ? "PREKO_TEME"
                    : otvoreniListovi.isEmpty() ? "ZATVORENO" : "DELIMICNO";
            rez.add(new OtvorenoZaGrupu(grupaId, (String) g.get("naziv"),
                    g.get("godina_upisa") == null ? null : ((Number) g.get("godina_upisa")).intValue(), stanje,
                    direktno.map(OtvorenaTema::getIzvor).orElse(null), direktno.map(OtvorenaTema::getPrviPut).orElse(null),
                    predak.map(z -> naziviPredaka.get(z.getTemaId())).orElse(null), otvoreniListovi.size(), listovi.size()));
        }
        return rez;
    }

    /** Ručno otvaranje čvora grupi (RUCNO); postojeći zapis ostaje kakav jeste. 404 za nepoznat čvor, TEST ili grupu. */
    public List<OtvorenoZaGrupu> otvori(Long temaId, Long grupaId) {
        cvor(temaId);
        grupa(grupaId);
        otvoreneRepository.otvoriAkoNema(grupaId, temaId, sada(), IzvorOtvaranja.RUCNO.name(), null);
        log.info("Ručno otvoreno za vežbanje: tema={}, grupa={}", temaId, grupaId);
        return otvoreno(temaId);
    }

    /**
     * Ručno zatvaranje: briše zapise čvora i celog njegovog podstabla za grupu, bez obzira na izvor (spec 12.3). Otvoren
     * predak ostaje otvoren (panel to kaže); ništa ga ne vraća samo.
     */
    public List<OtvorenoZaGrupu> zatvori(Long temaId, Long grupaId) {
        Cvor c = cvor(temaId);
        grupa(grupaId);
        int obrisano = otvoreneRepository.zatvori(grupaId, c.stablo().podstablo(temaId));
        log.info("Ručno zatvoreno za vežbanje: tema={}, grupa={}, zapisa={}", temaId, grupaId, obrisano);
        return otvoreno(temaId);
    }

    private record Cvor(Long predmetId, StabloPredavanja stablo, Opis opis) {
    }

    private Cvor cvor(Long temaId) {
        Long predmetId = grupaZadatakaRepository.findPredmetId(temaId)
                .orElseThrow(() -> new SystemException(NIJE_ZA_PREDAVANJA, HttpStatus.NOT_FOUND));
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Opis opis = stablo.opis(temaId);
        if (opis == null) throw new SystemException(NIJE_ZA_PREDAVANJA, HttpStatus.NOT_FOUND);
        return new Cvor(predmetId, stablo, opis);
    }

    private void grupa(Long grupaId) {
        Integer ima = jdbc.queryForObject("SELECT COUNT(*) FROM grupe WHERE id = ?", Integer.class, grupaId);
        if (ima == null || ima == 0) throw new SystemException(NEMA_GRUPE, HttpStatus.NOT_FOUND);
    }

    private List<Map<String, Object>> grupe(Long predmetId, Set<Long> grupeSaZapisom) {
        List<Map<String, Object>> sve = jdbc.queryForList("""
                SELECT g.id, g.naziv, g.godina_upisa FROM grupe g
                WHERE g.id IN (SELECT p.grupa_id FROM predavanja p WHERE p.predmet_id = ? AND p.grupa_id IS NOT NULL)
                ORDER BY g.godina_upisa DESC, g.naziv, g.id""", predmetId);
        Set<Long> vec = new HashSet<>();
        sve.forEach(g -> vec.add(((Number) g.get("id")).longValue()));
        List<Map<String, Object>> rez = new ArrayList<>(sve);
        for (Long id : grupeSaZapisom) {
            if (!vec.contains(id)) {
                rez.addAll(jdbc.queryForList("SELECT g.id, g.naziv, g.godina_upisa FROM grupe g WHERE g.id = ?", id));
            }
        }
        return rez;
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
