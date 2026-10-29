package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.portal.*;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.entity.portal.OtvorenaTema;
import tri.novica.gfssystem.entity.portal.Vezbanje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.portal.OtvorenaTemaRepository;
import tri.novica.gfssystem.repository.portal.VezbanjeRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.KandidatVarijante;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.banka.PoljaJson;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.service.uzivo.OcenaZadatka;
import tri.novica.gfssystem.service.uzivo.VarijantaSnimak;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Vežbanje na nalogu (spec studentskog dela 5.3, 5.4, 12.3): tipovi otvoreni grupama studenta, izvlačenje, provera i rešenje.
 * <p>
 * <b>Šta je otvoreno:</b> za svaki zapis u {@code otvorene_teme} grupa studenta, listovi njegovog čvora iz stabla PREDAVANJE
 * (bez isključenih podgrupa; sam čvor se ne preskače), isto kao izvlačenje na času ({@link KandidatiZadatka}). Zadaci su samo
 * AKTIVNE varijante sa SVG-om. Sve ostalo (zatvoren tip, TEST, nacrt, arhivirana, tuđi pokušaj) je isti 404.
 * <p>
 * <b>Šta izlazi:</b> pre "Prikaži rešenje" samo SVG teksta i šema polja; provera vraća tačno/netačno po polju, nikad tačne
 * vrednosti. Ocena je isti kod kao na času ({@link OcenaZadatka}), ali nad trenutnom varijantom i šemom. Bez bodova.
 * <p>
 * <b>Zadatak na času:</b> varijanta iz snimka runde izvođenja koje traje se ne izvlači, a pokušaj sa njom ne daje ni proveru
 * ni rešenje (409 {@value #NA_CASU}): inače bi student u drugom tabu izvlačio dok ne dobije zadatak sa table i uzeo rešenje.
 * <p>
 * <b>Cena izvlačenja:</b> bez "Novi zadatak" ({@code novi=true}) vraća se najnoviji netaknut pokušaj istog tipa i nivoa mlađi
 * od {@link #PONOVO_ISTI} (osvežavanje strane ne pravi nov red); svako novo izvlačenje se broji, najviše
 * {@value #LIMIT_IZVLACENJA} na sat po studentu (429). Netaknute pokušaje briše satno čišćenje ({@code CiscenjePrijave}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VezbanjeStudenta {

    static final int IZBEGAVAJ_POSLEDNJIH = 5;
    static final String NEMA_NIVOA = "Za ovaj nivo nema zadataka.";
    static final String NEMA_ZADATAKA = "Ovaj tip trenutno nema zadataka.";
    static final String NA_CASU = "Ovaj zadatak je upravo na času; pokušaj posle.";
    static final String SVI_NA_CASU = "Zadaci ovog tipa su upravo na času; pokušaj posle.";
    static final String PREVISE = "Previše novih zadataka za sat vremena. Reši započete ili pokušaj kasnije.";
    /** Koliko dugo se netaknut pokušaj vraća umesto novog izvlačenja. */
    static final Duration PONOVO_ISTI = Duration.ofMinutes(10);
    /** Najviše novih izvlačenja po studentu na sat ({@code pokusaji}, vrsta {@code VEZBANJE_IZVLACENJE}). */
    static final int LIMIT_IZVLACENJA = 60;
    private static final Duration SAT = Duration.ofHours(1);

    private final KontekstStudenta kontekstStudenta;
    private final OtvorenaTemaRepository otvoreneRepository;
    private final VezbanjeRepository vezbanjeRepository;
    private final VarijantaRepository varijantaRepository;
    private final GrupaZadatakaRepository grupaZadatakaRepository;
    private final KandidatiZadatka kandidati;
    private final PitanjeRundaRepository rundaRepository;
    private final PokusajiServis pokusaji;
    private final Random random;
    private final Clock clock;

    /** Teme i tipovi otvoreni za vežbanje na predmetu, redom stabla, sa brojem zadataka po nivou i sopstvenim učinkom. */
    public List<TemaVezbanja> stablo(long studentId, Long predmetId) {
        Kontekst k = kontekstStudenta.zaPredmet(studentId, predmetId);
        return stablo(k, predmetId);
    }

    /** Isto, za kontekst koji je pozivalac već proverio (tab "Predmeti"). */
    public List<TemaVezbanja> stablo(Kontekst k, Long predmetId) {
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        List<Long> listovi = otvoreniListovi(k, predmetId, stablo).stream()
                .filter(id -> stablo.opis(id).brojKandidata() > 0).toList();
        if (listovi.isEmpty()) return List.of();
        Map<Long, int[]> ucinak = new HashMap<>();
        for (Object[] r : vezbanjeRepository.ucinak(k.studentId(), listovi)) {
            ucinak.put(((Number) r[0]).longValue(), new int[]{((Number) r[1]).intValue(), ((Number) r[2]).intValue()});
        }
        Map<String, List<TipVezbanja>> poTemi = new LinkedHashMap<>();
        for (Long id : listovi) {
            Opis o = stablo.opis(id);
            List<GrupaPutanja> put = o.putanja();
            String tema = put.getFirst().naziv();
            List<String> izmedju = put.size() <= 2 ? List.of()
                    : put.subList(1, put.size() - 1).stream().map(GrupaPutanja::naziv).toList();
            int[] u = ucinak.getOrDefault(id, new int[]{0, 0});
            poTemi.computeIfAbsent(tema, t -> new ArrayList<>()).add(new TipVezbanja(id, o.naziv(), izmedju, o.poNivou(),
                    o.brojKandidata(), stablo.polja(id).isEmpty(), u[0], u[1]));
        }
        List<TemaVezbanja> rez = new ArrayList<>();
        poTemi.forEach((t, tipovi) -> rez.add(new TemaVezbanja(t, List.copyOf(tipovi))));
        return List.copyOf(rez);
    }

    /**
     * Listovi otvoreni grupama studenta <b>za taj predmet</b> ({@link Kontekst#grupe(Long)}) u stablu predmeta, redom stabla.
     * Zapis koji pokazuje na čvor van stabla (drugi predmet, TEST, obrisan) se preskače.
     */
    Set<Long> otvoreniListovi(Kontekst k, Long predmetId, StabloPredavanja stablo) {
        Set<Long> grupe = k.grupe(predmetId);
        if (grupe.isEmpty()) return Set.of();
        Set<Long> otvoreni = new HashSet<>();
        for (OtvorenaTema o : otvoreneRepository.findAllByGrupaIdIn(grupe)) {
            Opis op = stablo.opis(o.getTemaId());
            if (op != null) otvoreni.addAll(op.listovi());
        }
        Set<Long> redom = new LinkedHashSet<>();
        for (Long id : stablo.redom()) {
            if (otvoreni.contains(id)) redom.add(id);
        }
        return redom;
    }

    /**
     * Zadatak iz otvorenog tipa. Bez {@code novi=true} prvo najnoviji netaknut pokušaj istog tipa (i nivoa, kad je zadat)
     * mlađi od {@link #PONOVO_ISTI}, ako je njegova varijanta i dalje kandidat. Inače novo izvlačenje: nasumično
     * ({@code Random} bean) među kandidatima nivoa koji nisu na času, bez poslednjih {@value #IZBEGAVAJ_POSLEDNJIH} koje je
     * student video u tom tipu kad ima izbora (a bez poslednjeg kad je to moguće). 404 za tip koji nije otvoren list grupe
     * studenta za predmet; 409 kad nema kandidata ili su svi na času; 429 preko {@value #LIMIT_IZVLACENJA} novih na sat.
     */
    @Transactional
    public ZadatakVezbanja novi(long studentId, NoviZadatakCmd cmd) {
        Long tipId = cmd.tipId();
        Long predmetId = grupaZadatakaRepository.findPredmetId(tipId).orElseThrow(KontekstStudenta::nema);
        Kontekst k = kontekstStudenta.kontekst(studentId);
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Opis opis = stablo.opis(tipId);
        if (opis == null || !opis.list() || !otvoreniListovi(k, predmetId, stablo).contains(tipId)) throw KontekstStudenta.nema();
        List<KandidatVarijante> svi = kandidati.kandidati(stablo, tipId, cmd.nivo()).poListovima().stream()
                .flatMap(List::stream).toList();
        if (svi.isEmpty()) {
            throw new SystemException(cmd.nivo() == null ? NEMA_ZADATAKA : NEMA_NIVOA, HttpStatus.CONFLICT);
        }
        Set<Long> naCasu = naCasu(svi.stream().map(KandidatVarijante::varijantaId).toList());
        List<KandidatVarijante> kand = svi.stream().filter(v -> !naCasu.contains(v.varijantaId())).toList();
        if (kand.isEmpty()) throw new SystemException(SVI_NA_CASU, HttpStatus.CONFLICT);

        if (!Boolean.TRUE.equals(cmd.novi())) {
            Map<Long, KandidatVarijante> poId = new HashMap<>();
            kand.forEach(v -> poId.put(v.varijantaId(), v));
            for (Vezbanje staro : vezbanjeRepository.netaknuti(studentId, tipId, sada().minus(PONOVO_ISTI),
                    PageRequest.of(0, 10))) {
                KandidatVarijante v = poId.get(staro.getVarijantaId());
                if (v != null) return zadatak(staro, opis, v);
            }
        }
        String kljuc = Long.toString(studentId);
        if (pokusaji.dostignut(VrstaPokusaja.VEZBANJE_IZVLACENJE, kljuc, LIMIT_IZVLACENJA, SAT)) {
            throw new SystemException(PREVISE, HttpStatus.TOO_MANY_REQUESTS);
        }
        List<Long> poslednje = vezbanjeRepository.poslednjeVarijante(studentId, tipId, PageRequest.of(0, IZBEGAVAJ_POSLEDNJIH));
        List<KandidatVarijante> izbor = kand.stream().filter(v -> !poslednje.contains(v.varijantaId())).toList();
        if (izbor.isEmpty() && kand.size() > 1 && !poslednje.isEmpty()) {
            Long poslednja = poslednje.getFirst();
            izbor = kand.stream().filter(v -> !v.varijantaId().equals(poslednja)).toList();
        }
        if (izbor.isEmpty()) izbor = kand;
        KandidatVarijante v = izbor.get(random.nextInt(izbor.size()));

        pokusaji.zabeleziUTransakciji(VrstaPokusaja.VEZBANJE_IZVLACENJE, kljuc);
        Vezbanje vz = new Vezbanje();
        vz.setStudentId(studentId);
        vz.setVarijantaId(v.varijantaId());
        vz.setTemaId(tipId);
        vz.setIzvuceno(sada());
        vz = vezbanjeRepository.save(vz);
        return zadatak(vz, opis, v);
    }

    private static ZadatakVezbanja zadatak(Vezbanje vz, Opis opis, KandidatVarijante v) {
        return new ZadatakVezbanja(vz.getId(), opis.grupaId(), opis.naziv(), opis.putanja().getFirst().naziv(), v.nivo(), v.svgId(),
                v.polja() == null ? List.of() : v.polja());
    }

    /**
     * Provera odgovora (više puta): ocena po trenutnoj varijanti i šemi; 400 sa porukom za neispravan unos (isti tekstovi
     * kao na času), 404 za tuđ pokušaj ili varijantu koja više nije otvorena i aktivna, 409 dok je zadatak na času. Posle
     * "Prikaži rešenje" provera samo odgovara: ne broji se, ne menja "tačno iz prve" ni {@code tacno}.
     */
    @Transactional
    public ProveraVezbanja provera(long studentId, Long pokusajId, ProveraCmd cmd) {
        Vezbanje vz = vezbanjeRepository.findForUpdate(pokusajId, studentId).orElseThrow(KontekstStudenta::nema);
        VarijantaSnimak v = trenutna(studentId, vz);
        nijeNaCasu(vz.getVarijantaId());
        OcenaZadatka.Ishod ishod = OcenaZadatka.oceni(v, cmd == null ? null : cmd.polja());
        if (!vz.isResenjePrikazano()) {
            vz.setBrojProvera(vz.getBrojProvera() + 1);
            if (vz.getPrvaProveraTacno() == null) vz.setPrvaProveraTacno(ishod.tacno());
            vz.setTacno(ishod.tacno());
        }
        Map<String, Boolean> poPolju = new LinkedHashMap<>();
        ishod.polja().forEach((kljuc, p) -> poPolju.put(kljuc, p.tacno()));
        return new ProveraVezbanja(ishod.tacno(), poPolju);
    }

    /**
     * "Prikaži rešenje": SVG rešenja (ili {@code null}) i tačne vrednosti po polju šeme; beleži da je rešenje otvoreno. 409
     * dok je zadatak na času.
     */
    @Transactional
    public ResenjeVezbanja resenje(long studentId, Long pokusajId) {
        Vezbanje vz = vezbanjeRepository.findForUpdate(pokusajId, studentId).orElseThrow(KontekstStudenta::nema);
        VarijantaSnimak v = trenutna(studentId, vz);
        nijeNaCasu(vz.getVarijantaId());
        vz.setResenjePrikazano(true);
        Map<String, OdgovorPolja> tacne = new LinkedHashMap<>();
        if (v.polja() != null && v.odgovori() != null) {
            for (Polje p : v.polja()) {
                OdgovorPolja o = v.odgovori().get(p.kljuc());
                if (o != null) tacne.put(p.kljuc(), o);
            }
        }
        return new ResenjeVezbanja(v.resenjeSvgId(), tacne);
    }

    /** Varijante (od datih) koje su u snimku runde izvođenja koje traje. */
    private Set<Long> naCasu(Collection<Long> varijantaIds) {
        if (varijantaIds.isEmpty()) return Set.of();
        Set<Long> rez = new HashSet<>();
        for (Number n : rundaRepository.naCasu(varijantaIds)) rez.add(n.longValue());
        return rez;
    }

    private void nijeNaCasu(Long varijantaId) {
        if (!naCasu(List.of(varijantaId)).isEmpty()) throw new SystemException(NA_CASU, HttpStatus.CONFLICT);
    }

    /** Trenutna varijanta pokušaja, ako je i dalje AKTIVNA sa SVG-om i njen list otvoren grupi studenta; inače 404. */
    private VarijantaSnimak trenutna(long studentId, Vezbanje vz) {
        Varijanta var = varijantaRepository.findById(vz.getVarijantaId()).orElseThrow(KontekstStudenta::nema);
        if (var.getStatus() != StatusVarijante.AKTIVNA || var.getRenderSvg() == null) throw KontekstStudenta.nema();
        Long list = var.getGrupa().getId();
        Long predmetId = grupaZadatakaRepository.findPredmetId(list).orElseThrow(KontekstStudenta::nema);
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Opis opis = stablo.opis(list);
        if (opis == null || !opis.list()) throw KontekstStudenta.nema();
        if (!otvoreniListovi(kontekstStudenta.kontekst(studentId), predmetId, stablo).contains(list)) {
            throw KontekstStudenta.nema();
        }
        return new VarijantaSnimak(var.getId(), var.getNaziv(), list, var.getRenderSvg().getId(),
                var.getRenderResenjeSvg() == null ? null : var.getRenderResenjeSvg().getId(), stablo.polja(list),
                PoljaJson.odgovoriIzKolone(var.getOdgovori()), var.getNivo());
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
