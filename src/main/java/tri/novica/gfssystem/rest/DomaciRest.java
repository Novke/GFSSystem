package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.domaci.*;
import tri.novica.gfssystem.service.DomaciService;

@RestController
@RequestMapping("/domaci")
@RequiredArgsConstructor
public class DomaciRest {

    private final DomaciService domaciService;

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public DomaciInfo dodajDomaci(@RequestBody DodajDomaciCmd cmd){
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
}
