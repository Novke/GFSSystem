package tri.novica.gfssystem.rest.anketa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.anketa.*;
import tri.novica.gfssystem.entity.anketa.IzvorAnkete;
import tri.novica.gfssystem.service.anketa.AnketaSablonService;
import tri.novica.gfssystem.service.anketa.AnketaService;
import tri.novica.gfssystem.utility.PageableUtil;

import java.util.List;

/**
 * Brza anketa, nastavnik (spec brze ankete 5; ADMIN po {@code PristupMatrica}, nikad pod {@code /public/**}): sačuvana
 * pitanja, prečice i folderi, pokretanje, stanje, istorija, zatvaranje, rezultat i brisanje. Pravila su u
 * {@link AnketaService} i {@link AnketaSablonService}; nijedan odgovor ne nosi ko je za šta glasao.
 */
@RestController
@RequestMapping(path = "/ankete", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AnketaRest {

    private final AnketaService ankete;
    private final AnketaSablonService sabloni;

    // ---------------------------------------------------------------- šabloni, prečice, folderi

    @GetMapping("sabloni")
    public AnketeSabloniInfo sabloni() {
        return sabloni.sve();
    }

    @PostMapping("sabloni")
    @ResponseStatus(HttpStatus.CREATED)
    public SablonAnkete kreirajSablon(@RequestBody SablonAnketeCmd cmd) {
        return sabloni.kreiraj(cmd);
    }

    @PutMapping("sabloni/{id}")
    public SablonAnkete izmeniSablon(@PathVariable Long id, @RequestBody SablonAnketeCmd cmd) {
        return sabloni.izmeni(id, cmd);
    }

    @DeleteMapping("sabloni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisiSablon(@PathVariable Long id) {
        sabloni.obrisi(id);
    }

    @PutMapping("precice")
    public AnketeSabloniInfo precice(@Valid @RequestBody RedosledAnketaCmd cmd) {
        return sabloni.poredjajPrecice(cmd.ids());
    }

    @PostMapping("folderi")
    @ResponseStatus(HttpStatus.CREATED)
    public FolderAnkete kreirajFolder(@RequestBody FolderAnketeCmd cmd) {
        return sabloni.kreirajFolder(cmd.naziv());
    }

    @PutMapping("folderi/redosled")
    public AnketeSabloniInfo redosledFoldera(@Valid @RequestBody RedosledAnketaCmd cmd) {
        return sabloni.poredjajFoldere(cmd.ids());
    }

    @PutMapping("folderi/{id}")
    public FolderAnkete preimenujFolder(@PathVariable Long id, @RequestBody FolderAnketeCmd cmd) {
        return sabloni.preimenujFolder(id, cmd.naziv());
    }

    @DeleteMapping("folderi/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisiFolder(@PathVariable Long id) {
        sabloni.obrisiFolder(id);
    }

    // ---------------------------------------------------------------- ankete

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnketaDetails pokreni(@RequestBody PokreniAnketuCmd cmd) {
        return ankete.pokreni(cmd, IzvorAnkete.NASTAVNIK);
    }

    /** Otvorene i nedavno prikazani rezultati predavanja i/ili grupe (projektor, panel; front pita na 2 s). */
    @GetMapping("aktivne")
    public List<AnketaDetails> aktivne(@RequestParam(required = false) Long predavanjeId,
                                       @RequestParam(required = false) Long grupaId) {
        return ankete.aktivne(predavanjeId, grupaId);
    }

    /** Istorija, najnovije prve ({@code page}, {@code size}, {@code sort} samo po {@code otvoreno}). */
    @GetMapping
    public PagedModel<AnketaInfo> istorija(@RequestParam(required = false) Long predavanjeId,
                                           @RequestParam(required = false) Long grupaId, Pageable pageable) {
        return ankete.istorija(predavanjeId, grupaId,
                PageableUtil.proveri(pageable, AnketaService.SORT_POLJA, AnketaService.PODRAZUMEVANI_SORT));
    }

    @GetMapping("{id}")
    public AnketaDetails anketa(@PathVariable Long id) {
        return ankete.detalji(id);
    }

    @PostMapping("{id}/zatvori")
    public AnketaDetails zatvori(@PathVariable Long id) {
        return ankete.zatvori(id);
    }

    @PutMapping("{id}/rezultat")
    public AnketaDetails rezultat(@PathVariable Long id, @Valid @RequestBody PrikazRezultataCmd cmd) {
        return ankete.prikaziRezultat(id, cmd.prikazan());
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        ankete.obrisi(id);
    }
}
