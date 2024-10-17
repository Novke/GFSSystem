package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.domaci.DodajDomaciCmd;
import tri.novica.gfssystem.dto.domaci.DomaciInfo;
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
}
