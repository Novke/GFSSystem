package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.dto.uzivo.OpcijaDetails;
import tri.novica.gfssystem.dto.uzivo.PitanjeDetails;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.portal.*;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;
import tri.novica.gfssystem.service.uzivo.*;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Istorija studenta sa časa (spec studentskog dela 5.5, 6.1, 12.2): sopstveni zadaci sa časa i pregled prezentacija.
 * <p>
 * Sve samo iz <b>završenih</b> izvođenja ("ni ranije"): zadatak sa časa se vidi tek kad je izvođenje gotovo, slajd tek kad
 * je završeno neko izvođenje u kome ga je grupa videla (obrisano izvođenje više ništa ne otkriva). Zadaci se uvek vezuju za
 * učesnika prijavljenog kao taj student ({@code ucesnici.student_id}), slajdovi za grupe studenta za predmet prezentacije
 * ({@link KontekstStudenta.Kontekst#grupe(Long)}). Tuđe i nepostojeće je 404.
 * <p>
 * PITANJE slajd u pregledu nosi pitanje i tačan odgovor iz <b>snimka runde</b> završenog izvođenja sa grupom studenta (najnovije
 * takve runde), nikad iz trenutne verzije slajda: izmena slajda za drugu grupu ne otkriva novi odgovor ranijim grupama. Bez
 * takve runde (pitanje nije otvarano, ili "Ne čuvaj") ide trenutna verzija bez polja tačnog odgovora.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IstorijaStudenta {

    private static final TypeReference<LinkedHashMap<String, PoljeOdgovora>> POLJA = new TypeReference<>() {};

    private final KontekstStudenta kontekstStudenta;
    private final VezbanjeStudenta vezbanje;
    private final KandidatiZadatka kandidati;
    private final PrezentacijaRepository prezentacijaRepository;
    private final SlajdRepository slajdRepository;
    private final MedijRepository medijRepository;
    private final PlatnoJson platnoJson;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    // ---------------------------------------------------------------- moji zadaci sa časa

    /** Sopstvene dodele iz završenih izvođenja predmeta, od najnovije. */
    public List<MojZadatak> mojiZadaci(long studentId, Long predmetId) {
        Kontekst k = kontekstStudenta.zaPredmet(studentId, predmetId);
        return mojiZadaci(k, predmetId, null, null);
    }

    private List<MojZadatak> mojiZadaci(Kontekst k, Long predmetId, Long prezentacijaId, Long slajdId) {
        MapSqlParameterSource p = new MapSqlParameterSource("s", k.studentId()).addValue("p", predmetId)
                .addValue("prez", prezentacijaId).addValue("slajd", slajdId);
        List<Map<String, Object>> redovi = jdbc.queryForList("""
                SELECT d.runda_id, r.otvoreno, pz.id AS prez_id, pz.naziv AS prez_naziv, r.slajd_id, s.naslov, r.snimak,
                       d.varijanta_id, o.polja AS odg_polja, o.tacno AS odg_tacno
                FROM zadatak_dodele d
                JOIN ucesnici u ON u.id = d.ucesnik_id
                JOIN pitanje_runde r ON r.id = d.runda_id
                JOIN izvodjenja i ON i.id = r.izvodjenje_id
                JOIN prezentacije pz ON pz.id = i.prezentacija_id
                LEFT JOIN slajdovi s ON s.id = r.slajd_id
                LEFT JOIN odgovori o ON o.runda_id = r.id AND o.ucesnik_id = u.id
                WHERE u.student_id = :s AND i.status = 'ZAVRSENO' AND pz.predmet_id = :p
                  AND (:prez IS NULL OR pz.id = :prez) AND (:slajd IS NULL OR r.slajd_id = :slajd)
                ORDER BY r.otvoreno DESC, d.id DESC""", p);
        if (redovi.isEmpty()) return List.of();
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Set<Long> otvoreni = vezbanje.otvoreniListovi(k, predmetId, stablo);
        List<MojZadatak> rez = new ArrayList<>();
        for (Map<String, Object> r : redovi) {
            MojZadatak z = mojZadatak(r, stablo, otvoreni);
            if (z != null) rez.add(z);
        }
        return rez;
    }

    private MojZadatak mojZadatak(Map<String, Object> r, StabloPredavanja stablo, Set<Long> otvoreni) {
        Long rundaId = broj(r.get("runda_id"));
        if (!(snimak((String) r.get("snimak"), rundaId) instanceof ZadatakSnimak z)) return null;
        Optional<VarijantaSnimak> ov = z.varijanta(broj(r.get("varijanta_id")));
        if (ov.isEmpty()) return null;
        VarijantaSnimak v = ov.get();
        List<Polje> polja = v.polja() == null ? List.of() : v.polja();
        Map<String, PoljeOdgovora> odgovor = poljaOdgovora((String) r.get("odg_polja"), rundaId);
        Map<String, Boolean> ishod = null;
        if (!odgovor.isEmpty()) {
            ishod = new LinkedHashMap<>();
            for (Polje pp : polja) {
                PoljeOdgovora po = odgovor.get(pp.kljuc());
                if (po != null) ishod.put(pp.kljuc(), po.tacno());
            }
        }
        Map<String, OdgovorPolja> tacne = new LinkedHashMap<>();
        if (v.odgovori() != null) {
            for (Polje pp : polja) {
                OdgovorPolja o = v.odgovori().get(pp.kljuc());
                if (o != null) tacne.put(pp.kljuc(), o);
            }
        }
        Opis opisLista = v.grupaId() == null ? null : stablo.opis(v.grupaId());
        String tip = opisLista != null ? opisLista.naziv() : z.grupaNaziv();
        Boolean tacno = polja.isEmpty() || odgovor.isEmpty() ? null : (Boolean) bool(r.get("odg_tacno"));
        return new MojZadatak(rundaId, vreme(r.get("otvoreno")), broj(r.get("prez_id")), (String) r.get("prez_naziv"),
                broj(r.get("slajd_id")), (String) r.get("naslov"), tip, v.svgId(), v.resenjeSvgId(), polja,
                UnetoZadatka.tekstovi(v, odgovor), ishod, tacno, tacne, v.grupaId(),
                v.grupaId() != null && otvoreni.contains(v.grupaId()));
    }

    // ---------------------------------------------------------------- prezentacije

    /** Prezentacije predmeta sa bar jednim slajdom vidljivim studentu, od najnovije održane. */
    public List<PrezentacijaStudenta> prezentacije(long studentId, Long predmetId) {
        Kontekst k = kontekstStudenta.zaPredmet(studentId, predmetId);
        Set<Long> grupe = k.grupe(predmetId);
        if (grupe.isEmpty()) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource("g", grupe).addValue("p", predmetId);
        return jdbc.query("""
                SELECT pz.id, pz.naziv, COUNT(DISTINCT ps.slajd_id) AS slajdova,
                       (SELECT MIN(i2.pocetak) FROM izvodjenja i2
                        WHERE i2.prezentacija_id = pz.id AND i2.grupa_id IN (:g) AND i2.status = 'ZAVRSENO') AS odrzana
                FROM prikazani_slajdovi ps
                JOIN izvodjenja i ON i.id = ps.izvodjenje_id
                JOIN slajdovi s ON s.id = ps.slajd_id
                JOIN prezentacije pz ON pz.id = s.prezentacija_id
                WHERE ps.grupa_id IN (:g) AND pz.predmet_id = :p AND i.status = 'ZAVRSENO'
                GROUP BY pz.id, pz.naziv
                ORDER BY odrzana DESC, pz.id DESC""", p, (rs, i) -> new PrezentacijaStudenta(rs.getLong("id"),
                rs.getString("naziv"), vreme(rs.getTimestamp("odrzana")), rs.getInt("slajdova")));
    }

    /**
     * Pregled prezentacije: samo slajdovi koje je neka grupa studenta (za predmet prezentacije) videla u završenom izvođenju,
     * trenutna verzija, bez beleški; PITANJE iz snimka runde (vidi opis klase). 404 kad takvih slajdova nema (tuđa,
     * neodržana ili prezentacija čije izvođenje još traje).
     */
    public PregledPrezentacije pregled(long studentId, Long prezentacijaId) {
        if (prezentacijaId == null) throw KontekstStudenta.nema();
        Kontekst k = kontekstStudenta.kontekst(studentId);
        var prez = prezentacijaRepository.findById(prezentacijaId).orElseThrow(KontekstStudenta::nema);
        Long predmetId = prez.getPredmet().getId();
        Set<Long> grupe = k.grupe(predmetId);
        if (grupe.isEmpty()) throw KontekstStudenta.nema();
        Set<Long> vidljivi = new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT ps.slajd_id FROM prikazani_slajdovi ps
                JOIN izvodjenja i ON i.id = ps.izvodjenje_id
                JOIN slajdovi s ON s.id = ps.slajd_id
                WHERE ps.grupa_id IN (:g) AND s.prezentacija_id = :prez AND i.status = 'ZAVRSENO'""",
                new MapSqlParameterSource("g", grupe).addValue("prez", prezentacijaId), Long.class));
        if (vidljivi.isEmpty()) throw KontekstStudenta.nema();
        if (kontekstStudenta.predmeti(k).stream().noneMatch(pr -> pr.id().equals(predmetId))) throw KontekstStudenta.nema();

        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(prezentacijaId).stream()
                .filter(s -> vidljivi.contains(s.getId())).toList();
        List<Long> pitanja = slajdovi.stream().filter(s -> s.getTip() == TipSlajda.PITANJE).map(Slajd::getId).toList();
        Map<Long, OdgovorPregleda> odgovori = mojiOdgovori(k.studentId(), prezentacijaId, pitanja);
        Map<Long, PitanjeSnimak> snimciPitanja = snimciPitanja(grupe, prezentacijaId, pitanja);
        boolean imaZadatak = slajdovi.stream().anyMatch(s -> s.getTip() == TipSlajda.ZADATAK);
        StabloPredavanja stablo = imaZadatak ? kandidati.stablo(predmetId) : null;
        Set<Long> otvoreni = imaZadatak ? vezbanje.otvoreniListovi(k, predmetId, stablo) : Set.of();
        List<MojZadatak> moji = imaZadatak ? mojiZadaci(k, predmetId, prezentacijaId, null) : List.of();

        List<SlajdPregleda> rez = new ArrayList<>();
        for (Slajd s : slajdovi) {
            SlajdDetails d = SlajdMapper.uDetails(s, platnoJson, null);
            Platno platno = d.platno() == null ? null : d.platno().bezPoteza();
            ZadatakPregleda zadatak = null;
            if (s.getTip() == TipSlajda.ZADATAK) {
                Long cvor = s.getGrupaZadataka() == null ? null : s.getGrupaZadataka().getId();
                Opis opis = cvor == null ? null : stablo.opis(cvor);
                List<TipZaVezbanje> tipovi = opis == null ? List.of() : opis.listovi().stream()
                        .filter(otvoreni::contains).filter(id -> stablo.opis(id).brojKandidata() > 0)
                        .map(id -> new TipZaVezbanje(id, stablo.opis(id).naziv())).toList();
                zadatak = new ZadatakPregleda(opis == null ? null : opis.naziv(),
                        moji.stream().filter(m -> s.getId().equals(m.slajdId())).toList(), tipovi);
            }
            PitanjeDetails pitanje = null;
            boolean tacanPoznat = false;
            if (s.getTip() == TipSlajda.PITANJE) {
                PitanjeSnimak ps = snimciPitanja.get(s.getId());
                tacanPoznat = ps != null;
                pitanje = ps != null ? izSnimka(ps, d.pitanje()) : bezTacnog(d.pitanje());
            }
            rez.add(new SlajdPregleda(d.id(), d.rb(), d.tip(), d.naslov(), d.sadrzaj(), d.slika(), pitanje,
                    s.getTip() == TipSlajda.PLATNO ? platno : null, zadatak, odgovori.get(s.getId()), tacanPoznat));
        }
        return new PregledPrezentacije(prez.getId(), prez.getNaziv(), prez.getTema(), predmetId, List.copyOf(rez));
    }

    /**
     * Snimak pitanja po PITANJE slajdu: najnovija runda tog slajda u završenom izvođenju prezentacije sa nekom od grupa
     * studenta; nečitljiv snimak se preskače (uzima se sledeća runda).
     */
    private Map<Long, PitanjeSnimak> snimciPitanja(Set<Long> grupe, Long prezentacijaId, List<Long> slajdIds) {
        if (slajdIds.isEmpty() || grupe.isEmpty()) return Map.of();
        List<Map<String, Object>> redovi = jdbc.queryForList("""
                SELECT r.id AS runda_id, r.slajd_id, r.snimak
                FROM pitanje_runde r
                JOIN izvodjenja i ON i.id = r.izvodjenje_id
                WHERE i.status = 'ZAVRSENO' AND i.grupa_id IN (:g) AND i.prezentacija_id = :prez AND r.slajd_id IN (:ids)
                ORDER BY r.otvoreno DESC, r.id DESC""",
                new MapSqlParameterSource("g", grupe).addValue("prez", prezentacijaId).addValue("ids", slajdIds));
        Map<Long, PitanjeSnimak> rez = new HashMap<>();
        for (Map<String, Object> r : redovi) {
            Long slajdId = broj(r.get("slajd_id"));
            if (rez.containsKey(slajdId)) continue;
            if (snimak((String) r.get("snimak"), broj(r.get("runda_id"))) instanceof PitanjeSnimak ps) rez.put(slajdId, ps);
        }
        return rez;
    }

    /** Pitanje iz snimka runde (tekst, opcije, tačan odgovor); slika iz snimka (trenutni opis kad je ista). */
    private PitanjeDetails izSnimka(PitanjeSnimak ps, PitanjeDetails trenutno) {
        MedijInfo slika = null;
        if (ps.slikaId() != null) {
            slika = trenutno != null && trenutno.slika() != null && ps.slikaId().equals(trenutno.slika().id())
                    ? trenutno.slika()
                    : medijRepository.findById(ps.slikaId())
                            .map(m -> new MedijInfo(m.getId(), m.getNaziv(), m.getMime(), m.getVelicina())).orElse(null);
        }
        List<OpcijaDetails> opcije = ps.opcije() == null ? List.of() : ps.opcije().stream()
                .map(o -> new OpcijaDetails(o.id(), o.rb(), o.tekst(), o.tacna())).toList();
        return new PitanjeDetails(ps.pitanjeId(), ps.tip(), ps.tekst(), slika, ps.vremeSekunde(), opcije, ps.brojTacno(),
                ps.brojOdstupanje(), ps.odstupanjeTip(), ps.jedinica(), ps.tekstPrikaz(),
                ps.prihvatljiviOdgovori() == null ? null : List.copyOf(ps.prihvatljiviOdgovori()),
                ps.skalaMinOznaka(), ps.skalaMaxOznaka());
    }

    /** Trenutna verzija pitanja bez ičega što otkriva tačan odgovor (nijedna opcija tačna, bez broja i prihvatljivih). */
    static PitanjeDetails bezTacnog(PitanjeDetails p) {
        if (p == null) return null;
        List<OpcijaDetails> opcije = p.opcije() == null ? List.of() : p.opcije().stream()
                .map(o -> new OpcijaDetails(o.id(), o.rb(), o.tekst(), false)).toList();
        return new PitanjeDetails(p.id(), p.tip(), p.tekst(), p.slika(), p.vremeSekunde(), opcije, null, null, null,
                p.jedinica(), p.tekstPrikaz(), null, p.skalaMinOznaka(), p.skalaMaxOznaka());
    }

    /** Poslednji sopstveni odgovor po PITANJE slajdu, iz završenih izvođenja prezentacije. */
    private Map<Long, OdgovorPregleda> mojiOdgovori(long studentId, Long prezentacijaId, List<Long> slajdIds) {
        if (slajdIds.isEmpty()) return Map.of();
        List<Map<String, Object>> redovi = jdbc.queryForList("""
                SELECT r.id AS runda_id, r.slajd_id, r.snimak, o.opcije, o.broj, o.tekst, o.skala, o.tacno
                FROM odgovori o
                JOIN ucesnici u ON u.id = o.ucesnik_id
                JOIN pitanje_runde r ON r.id = o.runda_id
                JOIN izvodjenja i ON i.id = r.izvodjenje_id
                WHERE u.student_id = :s AND i.status = 'ZAVRSENO' AND i.prezentacija_id = :prez AND r.slajd_id IN (:ids)
                ORDER BY r.otvoreno DESC, r.id DESC""",
                new MapSqlParameterSource("s", studentId).addValue("prez", prezentacijaId).addValue("ids", slajdIds));
        Map<Long, OdgovorPregleda> rez = new HashMap<>();
        for (Map<String, Object> r : redovi) {
            Long slajdId = broj(r.get("slajd_id"));
            if (rez.containsKey(slajdId)) continue;
            if (!(snimak((String) r.get("snimak"), broj(r.get("runda_id"))) instanceof PitanjeSnimak ps)) continue;
            String tekst = tekstOdgovora(ps, r);
            if (tekst == null || tekst.isBlank()) continue;
            Boolean tacno = ps.tip() == TipPitanja.ANKETA ? null : bool(r.get("tacno"));
            rez.put(slajdId, new OdgovorPregleda(tekst, tacno));
        }
        return rez;
    }

    private static String tekstOdgovora(PitanjeSnimak ps, Map<String, Object> r) {
        String opcije = (String) r.get("opcije");
        if (opcije != null && !opcije.isBlank()) {
            Map<Long, String> tekstovi = new HashMap<>();
            if (ps.opcije() != null) ps.opcije().forEach(o -> tekstovi.put(o.id(), o.tekst()));
            List<String> izabrane = new ArrayList<>();
            for (String deo : opcije.split(",")) {
                try {
                    izabrane.add(tekstovi.getOrDefault(Long.parseLong(deo.strip()), "#" + deo.strip()));
                } catch (NumberFormatException e) {
                    // neispravan red: preskoči deo
                }
            }
            return String.join(", ", izabrane);
        }
        if (r.get("broj") instanceof Number n) {
            double d = n.doubleValue();
            String t = d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
            return ps.jedinica() == null || ps.jedinica().isBlank() ? t : t + " " + ps.jedinica().strip();
        }
        if (r.get("tekst") instanceof String t) return t;
        if (r.get("skala") instanceof Number n) return Integer.toString(n.intValue());
        return null;
    }

    // ---------------------------------------------------------------- pomoćno

    private SnimakRunde snimak(String json, Long rundaId) {
        try {
            return json == null ? null : jsonMapper.readValue(json, SnimakRunde.class);
        } catch (RuntimeException e) {
            log.warn("Snimak runde nije čitljiv (nalog studenta): runda={}", rundaId);
            return null;
        }
    }

    private Map<String, PoljeOdgovora> poljaOdgovora(String json, Long rundaId) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return jsonMapper.readValue(json, POLJA);
        } catch (RuntimeException e) {
            log.warn("Polja odgovora nisu čitljiva (nalog studenta): runda={}", rundaId);
            return Map.of();
        }
    }

    static Long broj(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    static Boolean bool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof byte[] b) return b.length > 0 && b[0] != 0;
        return null;
    }

    static LocalDateTime vreme(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime t) return t;
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }
}
