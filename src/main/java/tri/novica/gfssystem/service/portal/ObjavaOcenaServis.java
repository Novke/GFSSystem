package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.ocenjivanje.GetOceneCmd;
import tri.novica.gfssystem.dto.ocenjivanje.RezultatiStudentaInfo;
import tri.novica.gfssystem.dto.portal.ObjavaOcenaInfo;
import tri.novica.gfssystem.dto.portal.OcenaStudenta;
import tri.novica.gfssystem.dto.portal.PoeniTesta;
import tri.novica.gfssystem.dto.portal.SnimakOcene;
import tri.novica.gfssystem.entity.portal.ObjavaOcena;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.portal.ObjavljenaOcena;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.portal.ObjavaOcenaRepository;
import tri.novica.gfssystem.repository.portal.ObjavljenaOcenaRepository;
import tri.novica.gfssystem.service.OcenjivanjeService;
import tri.novica.gfssystem.service.portal.KontekstStudenta.Kontekst;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;
import tri.novica.gfssystem.service.push.PushPosiljalac;
import tri.novica.gfssystem.service.push.PushTekstovi;
import tri.novica.gfssystem.service.push.StudentskaObavestenja;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Objava predloga ocena (spec studentskog dela 12.4). Dok objave nema, student ne dobija ništa o oceni. "Objavi predloge"
 * pravi snimak po studentu grupe iz {@link OcenjivanjeService#getRezultati} (isti brojevi koje Novica vidi na Ocenama);
 * ponovna objava zamenjuje ceo snimak grupe; "Povuci" briše objavu i snimke. Student vidi samo svoj red objave sopstvene
 * grupe, i to samo objave iz tekuće školske godine: student koji ponavlja predmet (sopstvena grupa je starija generacija) ne
 * vidi prošlogodišnji predlog kao važeći.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ObjavaOcenaServis {

    /** TTL push poruke o ocenama (spec aplikacije 5.3: "ostalo 24 h"). */
    static final Duration OCENE_TTL = Duration.ofHours(24);

    private final ObjavaOcenaRepository objavaRepository;
    private final ObjavljenaOcenaRepository ocenaRepository;
    private final OcenjivanjeService ocenjivanjeService;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final KontekstStudenta kontekstStudenta;
    private final StudentskaObavestenja obavestenja;
    private final PushPosiljalac posiljalac;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ObjavaOcenaInfo stanje(Long predmetId, Long grupaId) {
        proveri(predmetId, grupaId);
        return objavaRepository.findByPredmetIdAndGrupaId(predmetId, grupaId)
                .map(o -> new ObjavaOcenaInfo(predmetId, grupaId, o.getObjavljeno(), ocenaRepository.countByObjavaId(o.getId())))
                .orElse(new ObjavaOcenaInfo(predmetId, grupaId, null, 0));
    }

    /**
     * Objavi (ili ponovo objavi) predloge za predmet i grupu: nov snimak za svakog studenta grupe. Studentima koji tu objavu
     * vide na nalogu ({@link #zaStudenta}) upisuje red zvona {@code OCENE} i posle commit-a šalje push (prekidač
     * {@code push_ocene}): "Ocene su objavljene", a kad je objava već postojala "Ocene su ažurirane"; bez broja.
     */
    public ObjavaOcenaInfo objavi(Long predmetId, Long grupaId) {
        proveri(predmetId, grupaId);
        List<RezultatiStudentaInfo> rezultati = ocenjivanjeService.getRezultati(predmetId, new GetOceneCmd(grupaId));
        LocalDateTime sada = sada();
        boolean ponovo = objavaRepository.findByPredmetIdAndGrupaId(predmetId, grupaId).isPresent();
        objavaRepository.obezbedi(predmetId, grupaId, sada);   // prvo red, pa brava (vidi obezbedi)
        ObjavaOcena o = objavaRepository.findForUpdate(predmetId, grupaId).orElseThrow();
        o.setObjavljeno(sada);
        o = objavaRepository.saveAndFlush(o);
        ocenaRepository.obrisiZaObjavu(o.getId());
        Set<Long> snimljeni = new LinkedHashSet<>();
        for (RezultatiStudentaInfo r : rezultati) {
            if (r.getStudentInfo() == null || r.getStudentInfo().getId() == null) continue;
            ObjavljenaOcena red = new ObjavljenaOcena();
            red.setObjavaId(o.getId());
            red.setStudentId(r.getStudentInfo().getId());
            red.setSnimak(jsonMapper.writeValueAsString(snimak(r)));
            ocenaRepository.save(red);
            snimljeni.add(red.getStudentId());
        }
        obavesti(predmetId, grupaId, sada, ponovo, snimljeni);
        log.info("Objavljeni predlozi ocena: predmet={}, grupa={}, studenata={}", predmetId, grupaId, rezultati.size());
        return new ObjavaOcenaInfo(predmetId, grupaId, o.getObjavljeno(), rezultati.size());
    }

    /**
     * Red zvona i push (spec aplikacije 6.3) samo studentima koji objavu vide: snimak postoji, sopstvena grupa je grupa
     * objave i student sluša predmet (isti uslovi kao {@link #zaStudenta}; objava je upravo napravljena, pa je iz tekuće
     * školske godine).
     */
    private void obavesti(Long predmetId, Long grupaId, LocalDateTime sada, boolean ponovo, Set<Long> snimljeni) {
        if (snimljeni.isEmpty()) return;
        List<Long> svoji = jdbc.queryForList("SELECT id FROM studenti WHERE grupa_id = :g AND id IN (:ids)",
                new MapSqlParameterSource("g", grupaId).addValue("ids", snimljeni), Long.class);
        Set<Long> primaoci = new LinkedHashSet<>();
        for (Long id : svoji) {
            if (kontekstStudenta.predmeti(kontekstStudenta.kontekst(id)).stream().anyMatch(p -> p.id().equals(predmetId))) {
                primaoci.add(id);
            }
        }
        if (primaoci.isEmpty()) return;
        String naziv = predmetRepository.findById(predmetId).map(Predmet::getNaziv).orElse("Predmet");
        String kljuc = "OCENE:" + predmetId + ":" + grupaId + ":" + sada;
        Set<Long> novi = obavestenja.upisi(primaoci, VrstaStudentskogObavestenja.OCENE, kljuc,
                PushTekstovi.ocene(predmetId, naziv, ponovo));
        posiljalac.uPozadiniPosleCommita(() -> obavestenja.posalji(kljuc, novi, Prekidac.PUSH_OCENE, OCENE_TTL, false));
    }

    /** Povuci objavu: studenti grupe opet ne vide ništa o oceni. Bez objave ne radi ništa. */
    public ObjavaOcenaInfo povuci(Long predmetId, Long grupaId) {
        proveri(predmetId, grupaId);
        objavaRepository.findForUpdate(predmetId, grupaId).ifPresent(o -> {
            ocenaRepository.obrisiZaObjavu(o.getId());
            objavaRepository.delete(o);
            log.info("Povučeni predlozi ocena: predmet={}, grupa={}", predmetId, grupaId);
        });
        return new ObjavaOcenaInfo(predmetId, grupaId, null, 0);
    }

    /** Ocena na nalogu: samo snimak objave sopstvene grupe studenta iz tekuće školske godine, inače "nije objavljeno". */
    @Transactional(readOnly = true)
    public OcenaStudenta zaStudenta(long studentId, Long predmetId) {
        Kontekst k = kontekstStudenta.zaPredmet(studentId, predmetId);
        if (k.svojaGrupa() == null) return OcenaStudenta.NIJE_OBJAVLJENO;
        return objavaRepository.findByPredmetIdAndGrupaId(predmetId, k.svojaGrupa()).filter(this::oveGodine)
                .flatMap(o -> ocenaRepository.findByObjavaIdAndStudentId(o.getId(), studentId)
                        .map(red -> new OcenaStudenta(true, o.getObjavljeno(),
                                jsonMapper.readValue(red.getSnimak(), SnimakOcene.class))))
                .orElse(OcenaStudenta.NIJE_OBJAVLJENO);
    }

    /** Da li student ima objavljen snimak za predmet (oznaka na tabu "Predmeti"). */
    @Transactional(readOnly = true)
    boolean objavljenoZa(Kontekst k, Long predmetId) {
        if (k.svojaGrupa() == null) return false;
        return objavaRepository.findByPredmetIdAndGrupaId(predmetId, k.svojaGrupa()).filter(this::oveGodine)
                .flatMap(o -> ocenaRepository.findByObjavaIdAndStudentId(o.getId(), k.studentId())).isPresent();
    }

    /** Objava iz tekuće školske godine (1. 10. do 30. 9., {@link SkolskaGodina}). */
    private boolean oveGodine(ObjavaOcena o) {
        return o.getObjavljeno() != null
                && SkolskaGodina.za(o.getObjavljeno().toLocalDate()) == SkolskaGodina.tekuca(clock);
    }

    private static SnimakOcene snimak(RezultatiStudentaInfo r) {
        List<PoeniTesta> testovi = r.getRezultati() == null ? List.of() : r.getRezultati().stream()
                .map(t -> new PoeniTesta(t.getTipTesta() == null ? null : t.getTipTesta().getNaziv(), t.getOstvarenoPoena()))
                .toList();
        return new SnimakOcene(r.getPredlogOcene(), r.getUkupno(), r.getPoeniPredispitne(), r.getPoeniAktivnost(),
                r.getPoeniDomaci(), testovi);
    }

    private void proveri(Long predmetId, Long grupaId) {
        if (predmetId == null || !predmetRepository.existsById(predmetId)) {
            throw new SystemException("Predmet ne postoji! ID = " + predmetId, HttpStatus.NOT_FOUND);
        }
        if (grupaId == null || !grupaRepository.existsById(grupaId)) {
            throw new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND);
        }
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
