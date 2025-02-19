package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.ocenjivanje.RezultatiStudentaInfo;
import tri.novica.gfssystem.dto.predmet.CreatePredmetCmd;
import tri.novica.gfssystem.dto.predmet.GetPredispitnePoeneCmd;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.service.PredmetService;

import java.util.List;

@RestController
@RequestMapping("/predmeti")
@RequiredArgsConstructor
public class PredmetRest {

    private final PredmetService predmetService;

    @GetMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<PredmetInfo> findAll(){
        return predmetService.findAll();
    }

    @GetMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredmetInfo findById(@PathVariable Long id){
        return predmetService.findById(id);
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public PredmetInfo createPredmet(@RequestBody CreatePredmetCmd predmetCmd){
        return predmetService.save(predmetCmd);
    }

    @GetMapping("/{id}/tipovi")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<TipTestaInfo> findTipovePredmeta(@PathVariable Long id){
        return predmetService.findTipovePredmeta(id);
    }

    @GetMapping("/{id}/poeni")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<RezultatiStudentaInfo> getUkupnePoene(@RequestBody @Valid GetPredispitnePoeneCmd cmd, @PathVariable Long id){
        return predmetService.getUkupnePoene(id,cmd);
    }

}
