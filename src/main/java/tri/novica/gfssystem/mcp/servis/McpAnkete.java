package tri.novica.gfssystem.mcp.servis;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.anketa.AnketaDetails;
import tri.novica.gfssystem.dto.anketa.AnketeSabloniInfo;
import tri.novica.gfssystem.dto.anketa.FolderAnkete;
import tri.novica.gfssystem.dto.anketa.PokreniAnketuCmd;
import tri.novica.gfssystem.dto.anketa.SablonAnkete;
import tri.novica.gfssystem.dto.anketa.SablonAnketeCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.anketa.AnketaSablonService;
import tri.novica.gfssystem.service.anketa.AnketaService;
import tri.novica.gfssystem.utility.Serija;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Fasada: brza anketa za Kloda (spec brze ankete 8). Zove samo {@link AnketaService} i {@link AnketaSablonService}: sačuvana
 * pitanja, folderi, prečice, pokretanje ({@code izvor = CLAUDE}), zatvaranje, prikaz rezultata i istorija sa brojevima po
 * odgovoru. Nijedan odgovor ne nosi studenta ni pojedinačan glas: {@link AnketaDetails} ima samo zbirove. Glasanje i
 * brisanje ankete nisu ovde.
 */
@Service
public class McpAnkete {

    private final AnketaService ankete;
    private final AnketaSablonService sabloni;

    public McpAnkete(AnketaService ankete, AnketaSablonService sabloni) {
        this.ankete = ankete;
        this.sabloni = sabloni;
    }

    /** Folderi, sačuvana pitanja i prečice (prečice prve, po redosledu traka). */
    public AnketeSabloniInfo sabloni() {
        return sabloni.sve();
    }

    /**
     * Novo sačuvano pitanje ili cela zamena po {@code id}; {@code folder} je naziv (pravi se kad ga nema, prazan = bez
     * foldera). Bez {@code id} se pitanje istog teksta menja umesto da nastane drugo.
     */
    public SablonAnkete sacuvajSablon(Long id, String folder, SablonAnketeCmd cmd, String serija) {
        Serija.proveri(serija);
        if (cmd == null) {
            throw new SystemException("Neispravan parametar: pitanje.", HttpStatus.BAD_REQUEST);
        }
        return sabloni.sacuvajZaKloda(id, folder, cmd);
    }

    /** Briše sačuvano pitanje; pokrenute ankete ostaju u istoriji. */
    public void obrisiSablon(Long id, String serija) {
        Serija.proveri(serija);
        sabloni.obrisi(obavezan(id, "id"));
    }

    /** Folder: sa {@code id} preimenovanje, bez njega postojeći istog naziva ili nov. */
    public FolderAnkete sacuvajFolder(Long id, String naziv, String serija) {
        Serija.proveri(serija);
        return sabloni.folderZaKloda(id, naziv);
    }

    /** Briše folder; njegova pitanja ostaju bez foldera. */
    public void obrisiFolder(Long id, String serija) {
        Serija.proveri(serija);
        sabloni.obrisiFolder(obavezan(id, "id"));
    }

    /** Nov redosled prečica (tačno ovi šabloni su prečice, ovim redom; prazan spisak = bez prečica). */
    public AnketeSabloniInfo poredjajPrecice(List<Long> ids, String serija) {
        Serija.proveri(serija);
        if (ids == null) {
            throw new SystemException("Neispravan parametar: ids.", HttpStatus.BAD_REQUEST);
        }
        return sabloni.poredjajPrecice(ids);
    }

    /** Najviše 50 najnovijih anketa po filteru, sa brojevima po odgovoru. */
    public List<AnketaDetails> istorija(Long predmetId, Long grupaId, Long predavanjeId) {
        return ankete.istorijaZaMcp(predmetId, grupaId, predavanjeId);
    }

    /**
     * Pokreće anketu ({@code izvor = CLAUDE}); {@code rok} je lokalno vreme ISO-8601 ({@code 2026-11-25T20:00}), a
     * {@code rokSati} rok od sada kad {@code rok} nije poslat.
     */
    public AnketaDetails pokreni(PokreniAnketuCmd cmd, String rok, Integer rokSati, String serija) {
        Serija.proveri(serija);
        LocalDateTime r = vreme(rok);
        PokreniAnketuCmd c = new PokreniAnketuCmd(cmd.sablonId(), cmd.pitanje(), cmd.opcije(), cmd.viseOdgovora(),
                cmd.publika(), cmd.predavanjeId(), cmd.grupaId(), cmd.trajanjeSekunde(), r);
        return ankete.pokreniZaKloda(c, rokSati);
    }

    /** Zatvara anketu; već zatvorena ostaje kakva jeste. */
    public AnketaDetails zatvori(Long id, String serija) {
        Serija.proveri(serija);
        return ankete.zatvori(obavezan(id, "id"));
    }

    /** Rezultat studentima i projektoru ({@code prikazan} podrazumevano {@code true}). */
    public AnketaDetails prikaziRezultat(Long id, Boolean prikazan, String serija) {
        Serija.proveri(serija);
        return ankete.prikaziRezultat(obavezan(id, "id"), prikazan == null || prikazan);
    }

    private static LocalDateTime vreme(String tekst) {
        if (tekst == null || tekst.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(tekst.strip());
        } catch (DateTimeParseException e) {
            throw new SystemException("Neispravan parametar: rok (lokalno vreme, npr. 2026-11-25T20:00).",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
