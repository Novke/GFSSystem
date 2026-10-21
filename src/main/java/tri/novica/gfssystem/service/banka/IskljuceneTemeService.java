package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaCmd;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.IskljucenaTema;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.IskljucenaTemaRepository;
import tri.novica.gfssystem.utility.Serija;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Isključene teme predmeta (spec MCP 6.2): tema sa grupom isključuje tu grupu i celo njeno podstablo (oznaku
 * {@code iskljucena} u stablu i grupi računa {@link GrupaZadatakaService}), tema bez grupe je samo opis teme koja u banci
 * još ne postoji, da je Klod ne napravi. Po predmetu najviše jedna tema po grupi ({@code UNIQUE(predmet_id, grupa_id)})
 * i najviše jedna tema bez grupe po normalizovanom opisu ({@link #normalizovan}; MySQL dozvoljava više NULL u UNIQUE, pa
 * to proverava servis), tako je ponovljen MCP {@code iskljuci_temu} uvek 409, nikad drugi red.
 * <p>
 * <b>Zaključavanje:</b> dodavanje prvo zaključa red predmeta (kao svaka izmena stabla u {@link GrupaZadatakaService}), pa
 * provera grupe, provera duplikata i upis idu redom po predmetu. Red grupe se ne zaključava: grupu briše ili premešta samo
 * {@code GrupaZadatakaService}, uvek pod ključem predmeta, pa grupa proverena posle tog ključa postoji do commit-a.
 * Uključivanje (brisanje teme) je jedan {@code DELETE} bez ključa predmeta: ne narušava nijedno pravilo dodavanja.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class IskljuceneTemeService {

    static final String NIJE_PRONADJENA = "Isključena tema nije pronađena.";
    static final String NEISPRAVAN_OPIS = "Opis teme mora imati od 1 do 300 znakova.";
    static final String GRUPA_DRUGOG_PREDMETA = "Grupa nije u ovom predmetu.";
    static final String GRUPA_ISKLJUCENA = "Ova grupa je već isključena.";
    static final String TEMA_ISKLJUCENA = "Ova tema je već isključena.";

    /** Kolona {@code opis} je {@code varchar(300)}: najviše 300 znakova (code point-a), posle trim-a. */
    static final int MAX_OPIS = 300;
    private static final Pattern RAZMACI = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final IskljucenaTemaRepository temaRepository;
    private final GrupaZadatakaRepository grupaRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaZadatakaService grupaService;
    private final Clock clock;

    /** Sve teme predmeta, najnovije prve; tema obrisane grupe ima {@code grupaId = null} i praznu putanju. */
    @Transactional(readOnly = true)
    public List<IskljucenaTemaInfo> lista(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (!predmetRepository.existsById(predmetId)) {
            throw predmetNijePronadjen();
        }
        return temaRepository.findByPredmetIdOrderByKreiranoDesc(predmetId).stream().map(this::info).toList();
    }

    /**
     * Isključuje temu: opis 1-{@value #MAX_OPIS} znakova (čuva se bez razmaka na krajevima), opciona grupa istog predmeta.
     * {@code izvor} ({@code null} = RUCNO) i {@code serija} (Klodova serija upisa, {@code null} za ručno isključenu; van
     * {@link Serija#OBRAZAC} 400) se upisuju na temu. Komanda i serija se proveravaju pre ključa predmeta, grupa i duplikati
     * pod njim: nepoznata grupa 404, grupa drugog predmeta 400, već isključena grupa 409 {@value #GRUPA_ISKLJUCENA}, tema
     * bez grupe sa istim normalizovanim opisom kao postojeća tema bez grupe 409 {@value #TEMA_ISKLJUCENA}.
     */
    public IskljucenaTemaInfo dodaj(Long predmetId, IskljucenaTemaCmd cmd, IzvorVarijante izvor, String serija) {
        if (cmd == null) {
            throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (serija != null) {
            Serija.proveri(serija);
        }
        String opis = proverenOpis(cmd.opis());
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        Predmet predmet = predmetRepository.findByIdForUpdate(predmetId).orElseThrow(IskljuceneTemeService::predmetNijePronadjen);

        GrupaZadataka grupa = null;
        if (cmd.grupaId() != null) {
            Long predmetGrupe = grupaRepository.findPredmetId(cmd.grupaId()).orElseThrow(IskljuceneTemeService::grupaNijePronadjena);
            if (!predmetGrupe.equals(predmetId)) {
                throw new SystemException(GRUPA_DRUGOG_PREDMETA, HttpStatus.BAD_REQUEST);
            }
            if (temaRepository.existsByPredmetIdAndGrupaId(predmetId, cmd.grupaId())) {
                throw new SystemException(GRUPA_ISKLJUCENA, HttpStatus.CONFLICT);
            }
            grupa = grupaRepository.findById(cmd.grupaId()).orElseThrow(IskljuceneTemeService::grupaNijePronadjena);
        } else {
            String kljuc = normalizovan(opis);
            boolean postoji = temaRepository.findByPredmetIdOrderByKreiranoDesc(predmetId).stream()
                    .anyMatch(t -> t.getGrupa() == null && normalizovan(t.getOpis()).equals(kljuc));
            if (postoji) {
                throw new SystemException(TEMA_ISKLJUCENA, HttpStatus.CONFLICT);
            }
        }

        IskljucenaTema t = new IskljucenaTema();
        t.setPredmet(predmet);
        t.setOpis(opis);
        t.setGrupa(grupa);
        t.setIzvor(izvor == null ? IzvorVarijante.RUCNO : izvor);
        t.setSerija(serija);
        t.setKreirano(LocalDateTime.now(clock));
        t = temaRepository.save(t);
        log.info("Tema isključena: id={}, predmet={}, grupa={}, izvor={}, serija={}", t.getId(), predmetId,
                grupa == null ? null : grupa.getId(), t.getIzvor(), serija);
        return info(t);
    }

    /** Uključuje temu (briše isključenje); nepoznata ili već obrisana tema je 404 {@value #NIJE_PRONADJENA}. */
    public void obrisi(Long id) {
        if (id == null || temaRepository.obrisiPoId(id) == 0) {
            throw new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
        }
        log.info("Isključena tema obrisana: id={}", id);
    }

    /** Isključene grupe predmeta: id grupe -> id njene teme (samo sopstvene teme, bez nasleđivanja na podstablo). */
    @Transactional(readOnly = true)
    public Map<Long, Long> iskljuceneGrupe(Long predmetId) {
        return temaRepository.iskljuceneGrupe(predmetId);
    }

    /**
     * Ključ za poređenje opisa tema bez grupe: bez razmaka na krajevima, svaki niz razmaka (i tab, novi red) sažet u jedan,
     * mala slova ({@link Locale#ROOT}).
     */
    static String normalizovan(String opis) {
        return RAZMACI.matcher(opis.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String proverenOpis(String opis) {
        String s = opis == null ? null : opis.strip();
        if (s == null || s.isEmpty() || s.codePointCount(0, s.length()) > MAX_OPIS) {
            throw new SystemException(NEISPRAVAN_OPIS, HttpStatus.BAD_REQUEST);
        }
        return s;
    }

    private IskljucenaTemaInfo info(IskljucenaTema t) {
        GrupaZadataka g = t.getGrupa();
        return new IskljucenaTemaInfo(t.getId(), t.getPredmet().getId(), t.getOpis(), g == null ? null : g.getId(),
                g == null ? List.of() : grupaService.putanja(g), t.getIzvor(), t.getSerija(), t.getKreirano());
    }

    private static SystemException predmetNijePronadjen() {
        return new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }

    private static SystemException grupaNijePronadjena() {
        return new SystemException(GrupaZadatakaService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }
}
