package tri.novica.gfssystem.service.anketa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.anketa.AnketeSabloniInfo;
import tri.novica.gfssystem.dto.anketa.FolderAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnketeCmd;
import tri.novica.gfssystem.entity.anketa.AnketaFolder;
import tri.novica.gfssystem.entity.anketa.AnketaSablon;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.anketa.AnketaFolderRepository;
import tri.novica.gfssystem.repository.anketa.AnketaSablonRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Sačuvana pitanja ankete, prečice i folderi (spec brze ankete 4 i 5). Isti servis koriste REST ({@code AnketaRest}) i MCP
 * fasada ({@code McpAnkete}). Granice (zaštita): najviše {@value #NAJVISE_SABLONA} šablona, {@value #NAJVISE_FOLDERA}
 * foldera i {@value #NAJVISE_PRECICA} prečica (409). Jedan nastavnik, pa se granice i redosled ne zaključavaju: dva
 * istovremena upisa mogu preći granicu za jedan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AnketaSablonService {

    public static final int NAJVISE_SABLONA = 200;
    public static final int NAJVISE_FOLDERA = 30;
    public static final int NAJVISE_PRECICA = 12;

    public static final String SABLON_NIJE_PRONADJEN = AnketaService.SABLON_NIJE_PRONADJEN;
    public static final String FOLDER_NIJE_PRONADJEN = "Folder nije pronađen.";
    public static final String PREVISE_SABLONA = "Sačuvano je već " + NAJVISE_SABLONA + " anketa. Obriši neku pre nove.";
    public static final String PREVISE_FOLDERA = "Već postoji " + NAJVISE_FOLDERA + " foldera.";
    public static final String PREVISE_PRECICA = "Najviše " + NAJVISE_PRECICA + " prečica.";
    public static final String ISTI_FOLDER = "Folder sa tim nazivom već postoji.";
    public static final String NEISPRAVAN_REDOSLED_FOLDERA = "Redosled mora sadržati sve foldere, svaki tačno jednom.";
    public static final String NEISPRAVNE_PRECICE = "Prečice moraju biti različiti postojeći šabloni.";

    private final AnketaSablonRepository sabloni;
    private final AnketaFolderRepository folderi;
    private final Clock clock;

    // ---------------------------------------------------------------- čitanje

    /** Folderi po {@code rb} i svi šabloni: prvo prečice po {@code precicaRb}, pa ostali po {@code rb} i {@code id}. */
    @Transactional(readOnly = true)
    public AnketeSabloniInfo sve() {
        Map<Long, Long> broj = new HashMap<>();
        for (Object[] r : sabloni.brojPoFolderu()) broj.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        List<FolderAnkete> f = folderi.findAllByOrderByRbAscIdAsc().stream()
                .map(x -> new FolderAnkete(x.getId(), x.getNaziv(), x.getRb(), broj.getOrDefault(x.getId(), 0L)))
                .toList();
        List<SablonAnkete> s = sabloni.findAll().stream()
                .sorted(Comparator.comparing((AnketaSablon x) -> !x.isPrecica())
                        .thenComparing(x -> x.isPrecica() && x.getPrecicaRb() != null ? x.getPrecicaRb() : Integer.MAX_VALUE)
                        .thenComparingInt(AnketaSablon::getRb)
                        .thenComparing(AnketaSablon::getId))
                .map(AnketaSablonService::dto)
                .toList();
        return new AnketeSabloniInfo(f, s);
    }

    @Transactional(readOnly = true)
    public SablonAnkete sablon(Long id) {
        return dto(nadji(id));
    }

    // ---------------------------------------------------------------- šabloni

    /** Novo sačuvano pitanje na kraj svog foldera (i prečica na kraj prečica kad je {@code precica}). */
    public SablonAnkete kreiraj(SablonAnketeCmd cmd) {
        if (sabloni.count() >= NAJVISE_SABLONA) throw new SystemException(PREVISE_SABLONA, HttpStatus.CONFLICT);
        LocalDateTime sada = sada();
        AnketaSablon s = new AnketaSablon();
        s.setKreirano(sada);
        primeni(s, cmd, sada, true);
        s = sabloni.saveAndFlush(s);
        log.info("Šablon ankete napravljen: id={}", s.getId());
        return dto(s);
    }

    /** Cela zamena: promena foldera stavlja šablon na kraj novog, uključena prečica ide na kraj prečica. */
    public SablonAnkete izmeni(Long id, SablonAnketeCmd cmd) {
        AnketaSablon s = nadji(id);
        primeni(s, cmd, sada(), false);
        return dto(sabloni.saveAndFlush(s));
    }

    /** Briše šablon; pokrenute ankete ostaju u istoriji ({@code ankete.sablon_id} {@code SET NULL}). */
    public void obrisi(Long id) {
        AnketaSablon s = nadji(id);
        sabloni.delete(s);
        sabloni.flush();
        log.info("Šablon ankete obrisan: id={}", id);
    }

    /**
     * Prečice tačno ovim redom (najviše {@value #NAJVISE_PRECICA}, različiti postojeći šabloni, inače 400/409); svi ostali
     * šabloni prestaju da budu prečice (izgled prečice ostaje sačuvan u šablonu).
     */
    public AnketeSabloniInfo poredjajPrecice(List<Long> ids) {
        List<Long> redosled = ids == null ? List.of() : ids;
        if (redosled.size() > NAJVISE_PRECICA) throw new SystemException(PREVISE_PRECICA, HttpStatus.CONFLICT);
        if (redosled.stream().anyMatch(Objects::isNull) || new HashSet<>(redosled).size() != redosled.size()) {
            throw new SystemException(NEISPRAVNE_PRECICE, HttpStatus.BAD_REQUEST);
        }
        Map<Long, AnketaSablon> po = new HashMap<>();
        sabloni.findAll().forEach(s -> po.put(s.getId(), s));
        if (!po.keySet().containsAll(redosled)) throw new SystemException(NEISPRAVNE_PRECICE, HttpStatus.BAD_REQUEST);
        LocalDateTime sada = sada();
        Map<Long, Integer> mesto = new HashMap<>();
        for (int i = 0; i < redosled.size(); i++) mesto.put(redosled.get(i), i + 1);
        for (AnketaSablon s : po.values()) {
            Integer rb = mesto.get(s.getId());
            boolean bila = s.isPrecica();
            Integer stariRb = s.getPrecicaRb();
            s.setPrecica(rb != null);
            s.setPrecicaRb(rb);
            if (bila != s.isPrecica() || !Objects.equals(stariRb, rb)) s.setIzmenjeno(sada);
        }
        sabloni.flush();
        return sve();
    }

    /**
     * Čuvanje za Kloda (MCP {@code sacuvaj_sablon_ankete}): folder po nazivu ({@link #folderPoNazivu}, prazan = bez foldera)
     * i cela zamena po {@code id}; bez {@code id} se šablon sa istim pitanjem (bez obzira na velika i mala slova) menja umesto
     * da nastane drugi, pa je ponovljen poziv bez posledica.
     */
    public SablonAnkete sacuvajZaKloda(Long id, String folder, SablonAnketeCmd cmd) {
        Long folderId = folder == null || folder.isBlank() ? null : folderPoNazivu(folder).id();
        SablonAnketeCmd saFolderom = new SablonAnketeCmd(folderId, cmd.pitanje(), cmd.opcije(), cmd.viseOdgovora(),
                cmd.publika(), cmd.trajanjeSekunde(), cmd.rokSati(), cmd.precica(), cmd.ikonica(), cmd.boja(),
                cmd.kratakNaziv(), cmd.pokreniOdmah());
        if (id != null) return izmeni(id, saFolderom);
        String pitanje = AnketaPravila.pitanje(cmd.pitanje());
        Optional<AnketaSablon> isti = sabloni.findAll().stream().filter(s -> isti(s.getPitanje(), pitanje))
                .min(Comparator.comparing(AnketaSablon::getId));
        return isti.isPresent() ? izmeni(isti.get().getId(), saFolderom) : kreiraj(saFolderom);
    }

    // ---------------------------------------------------------------- folderi

    /** Folder za Kloda (MCP {@code sacuvaj_folder_ankete}): sa {@code id} preimenovanje, bez njega postojeći istog naziva ili nov. */
    public FolderAnkete folderZaKloda(Long id, String naziv) {
        return id == null ? folderPoNazivu(naziv) : preimenujFolder(id, naziv);
    }

    public FolderAnkete kreirajFolder(String naziv) {
        String n = AnketaPravila.nazivFoldera(naziv);
        List<AnketaFolder> svi = folderi.findAll();
        if (svi.size() >= NAJVISE_FOLDERA) throw new SystemException(PREVISE_FOLDERA, HttpStatus.CONFLICT);
        if (svi.stream().anyMatch(f -> isti(f.getNaziv(), n))) throw new SystemException(ISTI_FOLDER, HttpStatus.CONFLICT);
        AnketaFolder f = new AnketaFolder();
        f.setNaziv(n);
        f.setRb(folderi.najveciRb() + 1);
        f.setKreirano(sada());
        f = folderi.saveAndFlush(f);
        return new FolderAnkete(f.getId(), f.getNaziv(), f.getRb(), 0);
    }

    /**
     * Folder po nazivu (MCP: šablon u folder po imenu): postojeći istog naziva bez obzira na velika i mala slova, inače nov.
     * Ponovljen poziv sa istim nazivom ne pravi drugi folder.
     */
    public FolderAnkete folderPoNazivu(String naziv) {
        String n = AnketaPravila.nazivFoldera(naziv);
        for (AnketaFolder f : folderi.findAll()) {
            if (isti(f.getNaziv(), n)) return folder(f);
        }
        return kreirajFolder(n);
    }

    public FolderAnkete preimenujFolder(Long id, String naziv) {
        String n = AnketaPravila.nazivFoldera(naziv);
        AnketaFolder f = nadjiFolder(id);
        if (folderi.findAll().stream().anyMatch(x -> !x.getId().equals(id) && isti(x.getNaziv(), n))) {
            throw new SystemException(ISTI_FOLDER, HttpStatus.CONFLICT);
        }
        f.setNaziv(n);
        return folder(folderi.saveAndFlush(f));
    }

    /** Redosled foldera: tačno svi folderi, svaki jednom (inače 400). */
    public AnketeSabloniInfo poredjajFoldere(List<Long> ids) {
        List<AnketaFolder> svi = folderi.findAll();
        Map<Long, AnketaFolder> po = new HashMap<>();
        svi.forEach(f -> po.put(f.getId(), f));
        if (ids == null || ids.size() != svi.size() || ids.stream().anyMatch(Objects::isNull)
                || !new HashSet<>(ids).equals(po.keySet())) {
            throw new SystemException(NEISPRAVAN_REDOSLED_FOLDERA, HttpStatus.BAD_REQUEST);
        }
        for (int i = 0; i < ids.size(); i++) po.get(ids.get(i)).setRb(i + 1);
        folderi.flush();
        return sve();
    }

    /** Briše folder; njegovi šabloni ostaju bez foldera, na kraju te liste. */
    public void obrisiFolder(Long id) {
        nadjiFolder(id);
        int pomak = sabloni.najveciRb(null);
        sabloni.bezFoldera(id, pomak);
        folderi.deleteById(id);
        folderi.flush();
        log.info("Folder anketa obrisan: id={}", id);
    }

    // ---------------------------------------------------------------- pomoćno

    private void primeni(AnketaSablon s, SablonAnketeCmd cmd, LocalDateTime sada, boolean nov) {
        String pitanje = AnketaPravila.pitanje(cmd.pitanje());
        List<String> odgovori = AnketaPravila.odgovori(cmd.opcije());
        PublikaAnkete publika = cmd.publika() == null ? PublikaAnkete.NA_CASU : cmd.publika();
        Integer trajanje = publika == PublikaAnkete.NA_CASU ? AnketaPravila.trajanje(cmd.trajanjeSekunde()) : null;
        Integer rokSati = publika == PublikaAnkete.GRUPA ? AnketaPravila.rokSati(cmd.rokSati()) : null;
        String ikonica = AnketaPravila.opcioniTekst(cmd.ikonica(), AnketaPravila.MAX_IKONICA, AnketaPravila.IKONICA);
        String kratak = AnketaPravila.opcioniTekst(cmd.kratakNaziv(), AnketaPravila.MAX_KRATAK_NAZIV,
                AnketaPravila.KRATAK_NAZIV);
        Long folderId = cmd.folderId();
        if (folderId != null && !folderi.existsById(folderId)) {
            throw new SystemException(FOLDER_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        }
        boolean precica = Boolean.TRUE.equals(cmd.precica());
        if (precica && !s.isPrecica()) {
            if (sabloni.countByPrecicaTrue() >= NAJVISE_PRECICA) throw new SystemException(PREVISE_PRECICA, HttpStatus.CONFLICT);
            s.setPrecicaRb(sabloni.najveciPrecicaRb() + 1);
        } else if (!precica) {
            s.setPrecicaRb(null);
        }
        if (nov || !Objects.equals(s.getFolderId(), folderId)) {
            s.setRb(sabloni.najveciRb(folderId) + 1);
        }
        s.setFolderId(folderId);
        s.setPitanje(pitanje);
        s.setOpcije(odgovori);
        s.setViseOdgovora(Boolean.TRUE.equals(cmd.viseOdgovora()));
        s.setPublika(publika);
        s.setTrajanjeSekunde(trajanje);
        s.setRokSati(rokSati);
        s.setPrecica(precica);
        s.setIkonica(ikonica);
        s.setBoja(cmd.boja());
        s.setKratakNaziv(kratak);
        s.setPokreniOdmah(Boolean.TRUE.equals(cmd.pokreniOdmah()));
        s.setIzmenjeno(sada);
    }

    private AnketaSablon nadji(Long id) {
        if (id == null) throw new SystemException(SABLON_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        return sabloni.findById(id).orElseThrow(() -> new SystemException(SABLON_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
    }

    private AnketaFolder nadjiFolder(Long id) {
        if (id == null) throw new SystemException(FOLDER_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        return folderi.findById(id).orElseThrow(() -> new SystemException(FOLDER_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
    }

    private FolderAnkete folder(AnketaFolder f) {
        long broj = sabloni.brojPoFolderu().stream().filter(r -> ((Number) r[0]).longValue() == f.getId())
                .mapToLong(r -> ((Number) r[1]).longValue()).findFirst().orElse(0);
        return new FolderAnkete(f.getId(), f.getNaziv(), f.getRb(), broj);
    }

    private static boolean isti(String a, String b) {
        return a.strip().toLowerCase(Locale.ROOT).equals(b.strip().toLowerCase(Locale.ROOT));
    }

    static SablonAnkete dto(AnketaSablon s) {
        return new SablonAnkete(s.getId(), s.getFolderId(), s.getPitanje(), s.getOpcije(), s.isViseOdgovora(), s.getPublika(),
                s.getTrajanjeSekunde(), s.getRokSati(), s.isPrecica(), s.getPrecicaRb(), s.getIkonica(), s.getBoja(),
                s.getKratakNaziv(), s.isPokreniOdmah(), s.getRb(), s.getBrojPokretanja(), s.getPoslednjePokretanje());
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
