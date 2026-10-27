package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.repository.uzivo.ZadatakDodelaRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.KandidatVarijante;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Kandidati;
import tri.novica.gfssystem.utility.Nivo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Runda Zadatak slajda (spec MCP 11.2, banka spec 4.2): snimak pri otvaranju (izvučene varijante iz
 * {@link KandidatiZadatka}, kroz {@link IzborVarijanti}) i dodela varijante svakom neizbačenom učesniku, najviše jedna po
 * rundi. Poziva se <b>samo dok pozivalac drži zaključan red izvođenja</b> (komanda otvaranja ili ponovnog otvaranja u
 * {@link IzvodjenjeService}, prijava u {@link UcesnikService}), pa dve dodele istom učesniku ne mogu nastati
 * istovremeno; {@code UNIQUE(runda_id, ucesnik_id)} je poslednja brana. Slučajnost samo iz bean-a {@link Random}.
 * <p>
 * <b>Ključevi varijanti:</b> {@link #snimak} posle ključa izvođenja zaključava kandidate čvora (redosled izvođenje ->
 * varijante, rastuće po id-ju) i izvlači samo one koji su posle ključa još AKTIVNI, pa brisanje varijante ili grupe
 * ({@code VarijantaService.koriscenoNaCasu}, koje zaključava grupu pa varijante i nikad ne čeka izvođenje) ili čeka
 * commit snimka i dobija 409, ili ga otvaranje čeka i obrisanu ne izvlači. Ponovno otvaranje iste runde i kasna prijava
 * ne zaključavaju varijante: snimak je tada već commit-ovan, a provera brisanja ga vidi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ZadatakDodelaService {

    static final String NEMA_AKTIVNIH = "Grupa nema aktivnih varijanti.";
    /** Kao {@link #NEMA_AKTIVNIH}, sa nivoom: {@code Grupa nema aktivnih varijanti nivoa ★★★.} */
    static final String NEMA_AKTIVNIH_NIVOA = "Grupa nema aktivnih varijanti nivoa %s.";
    static final String GRUPA_OBRISANA = "Grupa zadatka je obrisana.";

    private final KandidatiZadatka kandidati;
    private final ZadatakDodelaRepository dodelaRepository;
    private final UcesnikRepository ucesnikRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final VarijantaRepository varijantaRepository;
    private final JsonMapper jsonMapper;
    private final Random random;
    private final Clock clock;

    /**
     * Snimak nove runde ZADATAK slajda: {@code min(brojVarijanti, kandidata)} izvučenih ({@code null} = sve) nivoa
     * {@code nivo} ({@code null} = svi nivoi; nikad drugi nivo kad traženog nema), naizmenično po listovima, sa šemom i
     * tačnim odgovorima kakvi su sada. U snimku su redom stabla (list, pa id), ne redom
     * izvlačenja. Slajd bez grupe (obrisana) ili sa grupom koje više nema u stablu PREDAVANJE predmeta -> 409
     * {@value #GRUPA_OBRISANA}; bez kandidata -> 409 {@value #NEMA_AKTIVNIH} (sa nivoom {@value #NEMA_AKTIVNIH_NIVOA}). Ništa ne upisuje, ali pre izvlačenja
     * zaključava kandidate do kraja transakcije ({@link #zakljucaj}): nijedna izvučena varijanta ne može biti obrisana pre
     * nego što se snimak commit-uje, a izvlači se samo iz onih koje su posle ključa još AKTIVNE (nijedna više nije -> 409
     * {@value #NEMA_AKTIVNIH}).
     */
    public ZadatakSnimak snimak(Slajd s, Integer brojVarijanti, Integer nivo) {
        GrupaZadataka grupa = s.getGrupaZadataka();
        if (grupa == null) {
            throw new SystemException(GRUPA_OBRISANA, HttpStatus.CONFLICT);
        }
        Kandidati k = kandidati.kandidati(grupa.getId(), nivo);
        if (k.opis() == null) {
            throw new SystemException(GRUPA_OBRISANA, HttpStatus.CONFLICT);
        }
        List<List<KandidatVarijante>> poListovima = zakljucaj(k.poListovima(), nivo);
        if (poListovima.isEmpty()) {
            throw new SystemException(nivo == null ? NEMA_AKTIVNIH : NEMA_AKTIVNIH_NIVOA.formatted(Nivo.oznaka(nivo)),
                    HttpStatus.CONFLICT);
        }
        Set<Long> izvucene = new HashSet<>();
        IzborVarijanti.izvuci(poListovima, brojVarijanti, random).forEach(v -> izvucene.add(v.varijantaId()));
        List<VarijantaSnimak> varijante = poListovima.stream()
                .flatMap(List::stream)
                .filter(v -> izvucene.contains(v.varijantaId()))
                .map(ZadatakDodelaService::snimakVarijante)
                .toList();
        return new ZadatakSnimak(s.getId(), k.opis().grupaId(), k.opis().naziv(), k.opis().putanja(),
                s.getZadatakVremeSekunde(), varijante, nivo);
    }

    /**
     * Zaključava sve kandidate jednim upitom ({@link VarijantaRepository#zakljucajKandidate}: samo redovi varijanti,
     * rastuće po id-ju) i vraća listove samo sa onima koji su posle ključa još AKTIVNI sa SVG-om, bez listova koji su
     * ostali prazni (sa nivoom slajda i još tog nivoa). Varijanta obrisana, arhivirana ili prebačena u drugi nivo dok se runda
     * otvarala tako ne ulazi u izvlačenje. Brisanje varijante
     * ({@code VarijantaService}) i grupe ({@code GrupaZadatakaService}) zaključava grupu pa varijante, a izvođenje nikad,
     * pa redosled izvođenje -> varijante ne pravi krug. Sadržaj (SVG-ovi, šema, odgovori) ostaje iz čitanja pre ključa:
     * izmena commit-ovana između čitanja i ključa nije u snimku, kao da je runda otvorena malo pre nje. Snimak je i tada
     * ceo iz jedne verzije varijante, a njen stari SVG ostaje, jer je AKTIVNA varijanta odobrena ({@code ikadOdobrena},
     * {@code VarijantaService}, "SVG odobrene varijante se nikad ne briše").
     */
    private List<List<KandidatVarijante>> zakljucaj(List<List<KandidatVarijante>> poListovima, Integer nivo) {
        List<Long> ids = poListovima.stream().flatMap(List::stream).map(KandidatVarijante::varijantaId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<Long> zakljucane = new HashSet<>(nivo == null ? varijantaRepository.zakljucajKandidate(ids)
                : varijantaRepository.zakljucajKandidateNivoa(ids, nivo));
        if (zakljucane.size() == ids.size()) {
            return poListovima;
        }
        log.info("Otvaranje zadatka: {} od {} kandidata više nije AKTIVNO (obrisani ili izmenjeni u međuvremenu)",
                ids.size() - zakljucane.size(), ids.size());
        return poListovima.stream()
                .map(l -> l.stream().filter(v -> zakljucane.contains(v.varijantaId())).toList())
                .filter(l -> !l.isEmpty())
                .toList();
    }

    /**
     * Dodeljuje varijantu svakom neizbačenom učesniku izvođenja koji je još nema u rundi {@code r} (otvaranje i ponovno
     * otvaranje iste runde). Runda bez ijedne dodele deli ravnomerno ({@link IzborVarijanti#rasporedi}); inače svaki nov
     * (rastuće po id-ju) dobija najmanje dodeljenu, sa brojevima koji rastu posle svake dodele.
     */
    public void dodeliNedostajucima(Izvodjenje iz, PitanjeRunda r, ZadatakSnimak s) {
        Map<Long, Long> postojece = dodele(r);
        List<Ucesnik> novi = ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(iz.getId()).stream()
                .filter(u -> !u.isIzbacen() && !postojece.containsKey(u.getId()))
                .sorted(Comparator.comparing(Ucesnik::getId))
                .toList();
        if (novi.isEmpty()) {
            return;
        }
        dodeli(r, s, novi, postojece);
        log.info("Dodele zadatka: izvodjenje={}, runda={}, varijanti={}, novih={}, ukupno={}", iz.getId(), r.getId(),
                s.varijante().size(), novi.size(), postojece.size() + novi.size());
    }

    /**
     * Učesnik je upravo ušao (nov ili isti student sa drugog telefona): dobija dodelu samo dok je trenutna runda
     * ZADATAK runda u fazi OTVORENO i on je još nema (najmanje dodeljena varijanta). U ostalim fazama dodelu dobija pri
     * (ponovnom) otvaranju.
     */
    public void dodeliNovom(Izvodjenje iz, Ucesnik u) {
        if (iz.getFaza() != Faza.OTVORENO || iz.getTrenutnaRundaId() == null || u.isIzbacen()) {
            return;
        }
        PitanjeRunda r = rundaRepository.findById(iz.getTrenutnaRundaId()).orElse(null);
        if (r == null || !(jsonMapper.readValue(r.getSnimak(), SnimakRunde.class) instanceof ZadatakSnimak s)) {
            return;
        }
        Map<Long, Long> postojece = dodele(r);
        if (postojece.containsKey(u.getId())) {
            return;
        }
        dodeli(r, s, List.of(u), postojece);
    }

    /** Dodele runde: učesnik -> varijanta. */
    private Map<Long, Long> dodele(PitanjeRunda r) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] red : dodelaRepository.parovi(r.getId())) {
            m.put(((Number) red[0]).longValue(), ((Number) red[1]).longValue());
        }
        return m;
    }

    private void dodeli(PitanjeRunda r, ZadatakSnimak s, List<Ucesnik> novi, Map<Long, Long> postojece) {
        List<Long> varijante = s.varijante().stream().map(VarijantaSnimak::varijantaId).toList();
        Map<Long, Long> raspored = new LinkedHashMap<>();
        if (postojece.isEmpty()) {
            raspored.putAll(IzborVarijanti.rasporedi(novi.stream().map(Ucesnik::getId).toList(), varijante, random));
        } else {
            Map<Long, Integer> broj = new HashMap<>();
            postojece.values().forEach(v -> broj.merge(v, 1, Integer::sum));
            for (Ucesnik u : novi) {
                Long v = IzborVarijanti.najmanjeDodeljena(varijante, broj, random);
                broj.merge(v, 1, Integer::sum);
                raspored.put(u.getId(), v);
            }
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        for (Ucesnik u : novi) {
            ZadatakDodela d = new ZadatakDodela();
            d.setRunda(r);
            d.setUcesnik(u);
            d.setVarijanta(varijantaRepository.getReferenceById(raspored.get(u.getId())));
            d.setKreirano(sada);
            dodelaRepository.save(d);
        }
    }

    private static VarijantaSnimak snimakVarijante(KandidatVarijante v) {
        return new VarijantaSnimak(v.varijantaId(), v.naziv(), v.grupaId(), v.svgId(), v.resenjeSvgId(), v.polja(),
                v.odgovori(), v.nivo());
    }
}
