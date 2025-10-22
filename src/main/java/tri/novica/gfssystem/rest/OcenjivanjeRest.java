package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.ocenjivanje.GetOceneCmd;
import tri.novica.gfssystem.dto.ocenjivanje.KoeficijentiInfo;
import tri.novica.gfssystem.dto.ocenjivanje.RezultatiStudentaInfo;
import tri.novica.gfssystem.dto.ocenjivanje.SaveKoeficijentiCmd;
import tri.novica.gfssystem.service.OcenjivanjeService;

import java.util.List;

@RestController
@RequestMapping("/ocenjivanje")
@RequiredArgsConstructor
public class OcenjivanjeRest {

    private final OcenjivanjeService ocenjivanjeService;

    @GetMapping("/predmet/{predmetId}/koeficijenti")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public KoeficijentiInfo getKoeficijenti(@PathVariable Long predmetId) {
        return ocenjivanjeService.getKoeficijenti(predmetId);
    }

    @PostMapping("/predmet/{predmetId}/koeficijenti")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public KoeficijentiInfo saveKoeficijenti(@PathVariable Long predmetId,
                                              @RequestBody @Valid SaveKoeficijentiCmd cmd) {
        return ocenjivanjeService.saveKoeficijenti(predmetId, cmd);
    }

    @PostMapping("/predmet/{predmetId}/rezultati")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<RezultatiStudentaInfo> getRezultati(@PathVariable Long predmetId,
                                                     @RequestBody @Valid GetOceneCmd cmd) {
        return ocenjivanjeService.getRezultati(predmetId, cmd);
    }
}
