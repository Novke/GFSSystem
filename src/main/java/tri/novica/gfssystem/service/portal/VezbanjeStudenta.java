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
import tri.novica.gfssystem.entity.portal.OtvorenaTema;
import tri.novica.gfssystem.entity.portal.Vezbanje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.portal.OtvorenaTemaRepository;
import tri.novica.gfssystem.repository.portal.VezbanjeRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.KandidatVarijante;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.banka.PoljaJson;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;
import tri.novica.gfssystem.service.uzivo.OcenaZadatka;
import tri.novica.gfssystem.service.uzivo.VarijantaSnimak;

import java.time.Clock;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VezbanjeStudenta {

    static final int IZBEGAVAJ_POSLEDNJIH = 5;
    static final String NEMA_NIVOA = "Za ovaj nivo nema zadataka.";
    static final String NEMA_ZADATAKA = "Ovaj tip trenutno nema zadataka.";

    private final KontekstStudenta kontekstStudenta;
    private final OtvorenaTemaRepository otvoreneRepository;
    private final VezbanjeRepository vezbanjeRepository;
    private final VarijantaRepository varijantaRepository;
    private final GrupaZadatakaRepository grupaZadatakaRepository;
    private final KandidatiZadatka kandidati;
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
        List<Long> listovi = otvoreniListovi(k, stablo).stream()
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
     * Listovi otvoreni grupama studenta u ovom stablu, redom stabla. Zapis koji pokazuje na čvor van stabla (drugi predmet,
     * TEST, obrisan) se preskače.
     */
    Set<Long> otvoreniListovi(Kontekst k, StabloPredavanja stablo) {
        if (k.grupe().isEmpty()) return Set.of();
        Set<Long> otvoreni = new HashSet<>();
        for (OtvorenaTema o : otvoreneRepository.findAllByGrupaIdIn(k.grupe())) {
            Opis op = stablo.opis(o.getTemaId());
            if (op != null) otvoreni.addAll(op.listovi());
        }
        Set<Long> redom = new LinkedHashSet<>();
        for (Long id : stablo.redom()) {
            if (otvoreni.contains(id)) redom.add(id);
        }
        return redom;
    }

    /** Listovi otvoreni studentu u predmetu (za "Vežbaj ovaj tip" na drugim stranama). */
    public Set<Long> otvoreniListovi(Kontekst k, Long predmetId) {
        return otvoreniListovi(k, kandidati.stablo(predmetId));
    }

    /**
     * Nov zadatak iz otvorenog tipa: nasumično ({@code Random} bean) među kandidatima nivoa, bez poslednjih
     * {@value #IZBEGAVAJ_POSLEDNJIH} koje je student video u tom tipu kad ima izbora (a bez poslednjeg kad je to moguće).
     * 404 za tip koji nije otvoren list grupe studenta; 409 kad nema kandidata.
     */
    @Transactional
    public ZadatakVezbanja novi(long studentId, NoviZadatakCmd cmd) {
        Long tipId = cmd.tipId();
        Long predmetId = grupaZadatakaRepository.findPredmetId(tipId).orElseThrow(KontekstStudenta::nema);
        Kontekst k = kontekstStudenta.kontekst(studentId);
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Opis opis = stablo.opis(tipId);
        if (opis == null || !opis.list() || !otvoreniListovi(k, stablo).contains(tipId)) throw KontekstStudenta.nema();
        List<KandidatVarijante> kand = kandidati.kandidati(tipId, cmd.nivo()).poListovima().stream()
                .flatMap(List::stream).toList();
        if (kand.isEmpty()) {
            throw new SystemException(cmd.nivo() == null ? NEMA_ZADATAKA : NEMA_NIVOA, HttpStatus.CONFLICT);
        }
        List<Long> poslednje = vezbanjeRepository.poslednjeVarijante(studentId, tipId, PageRequest.of(0, IZBEGAVAJ_POSLEDNJIH));
        List<KandidatVarijante> izbor = kand.stream().filter(v -> !poslednje.contains(v.varijantaId())).toList();
        if (izbor.isEmpty() && kand.size() > 1 && !poslednje.isEmpty()) {
            Long poslednja = poslednje.getFirst();
            izbor = kand.stream().filter(v -> !v.varijantaId().equals(poslednja)).toList();
        }
        if (izbor.isEmpty()) izbor = kand;
        KandidatVarijante v = izbor.get(random.nextInt(izbor.size()));

        Vezbanje vz = new Vezbanje();
        vz.setStudentId(studentId);
        vz.setVarijantaId(v.varijantaId());
        vz.setTemaId(tipId);
        vz.setIzvuceno(sada());
        vz = vezbanjeRepository.save(vz);
        return new ZadatakVezbanja(vz.getId(), tipId, opis.naziv(), opis.putanja().getFirst().naziv(), v.nivo(), v.svgId(),
                v.polja() == null ? List.of() : v.polja());
    }

    /**
     * Provera odgovora (više puta): ocena po trenutnoj varijanti i šemi; 400 sa porukom za neispravan unos (isti tekstovi
     * kao na času), 404 za tuđ pokušaj ili varijantu koja više nije otvorena i aktivna.
     */
    @Transactional
    public ProveraVezbanja provera(long studentId, Long pokusajId, ProveraCmd cmd) {
        Vezbanje vz = vezbanjeRepository.findForUpdate(pokusajId, studentId).orElseThrow(KontekstStudenta::nema);
        VarijantaSnimak v = trenutna(studentId, vz);
        OcenaZadatka.Ishod ishod = OcenaZadatka.oceni(v, cmd == null ? null : cmd.polja());
        vz.setBrojProvera(vz.getBrojProvera() + 1);
        if (vz.getPrvaProveraTacno() == null) vz.setPrvaProveraTacno(ishod.tacno());
        vz.setTacno(ishod.tacno());
        Map<String, Boolean> poPolju = new LinkedHashMap<>();
        ishod.polja().forEach((kljuc, p) -> poPolju.put(kljuc, p.tacno()));
        return new ProveraVezbanja(ishod.tacno(), poPolju);
    }

    /** "Prikaži rešenje": SVG rešenja (ili {@code null}) i tačne vrednosti po polju šeme; beleži da je rešenje otvoreno. */
    @Transactional
    public ResenjeVezbanja resenje(long studentId, Long pokusajId) {
        Vezbanje vz = vezbanjeRepository.findForUpdate(pokusajId, studentId).orElseThrow(KontekstStudenta::nema);
        VarijantaSnimak v = trenutna(studentId, vz);
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

    /** Trenutna varijanta pokušaja, ako je i dalje AKTIVNA sa SVG-om i njen list otvoren grupi studenta; inače 404. */
    private VarijantaSnimak trenutna(long studentId, Vezbanje vz) {
        Varijanta var = varijantaRepository.findById(vz.getVarijantaId()).orElseThrow(KontekstStudenta::nema);
        if (var.getStatus() != StatusVarijante.AKTIVNA || var.getRenderSvg() == null) throw KontekstStudenta.nema();
        Long list = var.getGrupa().getId();
        Long predmetId = grupaZadatakaRepository.findPredmetId(list).orElseThrow(KontekstStudenta::nema);
        StabloPredavanja stablo = kandidati.stablo(predmetId);
        Opis opis = stablo.opis(list);
        if (opis == null || !opis.list()) throw KontekstStudenta.nema();
        if (!otvoreniListovi(kontekstStudenta.kontekst(studentId), stablo).contains(list)) throw KontekstStudenta.nema();
        return new VarijantaSnimak(var.getId(), var.getNaziv(), list, var.getRenderSvg().getId(),
                var.getRenderResenjeSvg() == null ? null : var.getRenderResenjeSvg().getId(), stablo.polja(list),
                PoljaJson.odgovoriIzKolone(var.getOdgovori()), var.getNivo());
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
