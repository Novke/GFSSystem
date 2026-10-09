package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.StanjeService;

import java.util.List;

/**
 * Nastavničko vođenje izvođenja (iza basic-auth-a): komande, stanje, moderacija, pregled. Pokretanje je
 * {@code POST /prezentacije/{id}/izvodjenja} u {@link PrezentacijaRest}. Komande idu REST-om (jasne greške), a odgovor
 * je novo stanje; isto stanje stiže i drugim prozorima kroz WebSocket.
 */
@RestController
@RequestMapping(path = "/izvodjenja", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class IzvodjenjeRest {

    private final IzvodjenjeService izvodjenjeService;
    private final StanjeService stanjeService;

    /** Najnovija prva; oba filtra su opciona. */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<IzvodjenjeInfo> lista(@RequestParam(required = false) Long prezentacijaId,
                                      @RequestParam(required = false) StatusIzvodjenja status) {
        return izvodjenjeService.lista(prezentacijaId, status);
    }

    @GetMapping("/{id}/stanje")
    @ResponseStatus(HttpStatus.OK)
    public NastavnickoStanje stanje(@PathVariable Long id) {
        return stanjeService.nastavnicko(id);
    }

    @PostMapping("/{id}/komande")
    @ResponseStatus(HttpStatus.OK)
    public NastavnickoStanje komanda(@PathVariable Long id, @RequestBody KomandaCmd cmd) {
        return izvodjenjeService.komanda(id, cmd);
    }

    @PutMapping("/{id}/ucesnici/{ucesnikId}")
    @ResponseStatus(HttpStatus.OK)
    public NastavnickoStanje preimenuj(@PathVariable Long id, @PathVariable Long ucesnikId,
                                       @RequestBody PreimenujCmd cmd) {
        return izvodjenjeService.preimenuj(id, ucesnikId, cmd.ime());
    }

    /** Izbacivanje učesnika. */
    @DeleteMapping("/{id}/ucesnici/{ucesnikId}")
    @ResponseStatus(HttpStatus.OK)
    public NastavnickoStanje izbaci(@PathVariable Long id, @PathVariable Long ucesnikId) {
        return izvodjenjeService.izbaci(id, ucesnikId);
    }

    @PutMapping("/{id}/runde/{rundaId}/sakrij")
    @ResponseStatus(HttpStatus.OK)
    public NastavnickoStanje sakrij(@PathVariable Long id, @PathVariable Long rundaId, @RequestBody SakrijCmd cmd) {
        return izvodjenjeService.sakrij(id, rundaId, cmd);
    }

    @GetMapping("/{id}/rezultati")
    @ResponseStatus(HttpStatus.OK)
    public IzvodjenjeRezultati rezultati(@PathVariable Long id) {
        return izvodjenjeService.rezultati(id);
    }

    /** Samo završeno izvođenje (aktivno -> 409). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        izvodjenjeService.obrisi(id);
    }
}
