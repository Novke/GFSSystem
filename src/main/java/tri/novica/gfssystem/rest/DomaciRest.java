package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.domaci.*;
import tri.novica.gfssystem.service.DomaciService;
import tri.novica.gfssystem.utility.PageableUtil;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/domaci")
@RequiredArgsConstructor
public class DomaciRest {

    private final DomaciService domaciService;

    /**
     * Lista domaćih za UI: filteri su opcioni, {@code page}/{@code size} (podrazumevano 25, najviše 100) i
     * {@code sort} po {@code datum} ili {@code naslov} (podrazumevano {@code datum,desc}).
     * Literal {@code /pretraga} ima prednost nad {@code /{id}}.
     */
    @GetMapping("/pretraga")
    @ResponseStatus(HttpStatus.OK)
    public PagedModel<DomaciListItem> pretraga(@RequestParam(required = false) Long predmetId,
            @RequestParam(required = false) Long grupaId, @RequestParam(required = false) Integer godina,
            @RequestParam(required = false) Boolean pregledan, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
            @RequestParam(name = "do", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doDatuma,
            Pageable pageable) {
        return domaciService.pretraga(new DomaciFilter(predmetId, grupaId, godina, pregledan, q, od, doDatuma),
                PageableUtil.proveri(pageable, DomaciService.SORT_POLJA, DomaciService.PODRAZUMEVANI_SORT));
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public DomaciId dodajDomaci(@RequestBody DodajDomaciCmd cmd){
        return domaciService.dodajDomaci(cmd);
    }

    @GetMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public DomaciDetails view(@PathVariable Long id){
        return domaciService.getDomaci(id);
    }

    @PostMapping("/evidentiraj")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public DomaciDetails dodajEvidentaciju(@RequestBody @Valid CreateUradjenDomaciCmd cmd){
        return domaciService.dodajEvidentaciju(cmd);
    }

    @PostMapping("/{id}/oslobodi")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public DomaciDetails oslobodi(@PathVariable Long id){
        return domaciService.oslobodi(id);
    }

    @PutMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public DomaciDetails azuriraj(@PathVariable Long id, @RequestBody UpdateDomaciCmd cmd){
        return domaciService.azuriraj(id, cmd);
    }

    @PatchMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public void zavrsiPregledanje(@PathVariable Long id){
        domaciService.zavrsiPregledanje(id);
    }

    @GetMapping("/grupa/{gId}/predmet/{pId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<DomaciInfo> vratiDomaceGrupaPredmet(@PathVariable Long gId, @PathVariable Long pId){
        return domaciService.vratiDomaceIzGrupaPredmet(gId, pId);
    }
}
