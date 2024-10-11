package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.predmet.CreatePredmetCmd;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
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

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public PredmetInfo createPredmet(@RequestBody CreatePredmetCmd predmetCmd){
        return predmetService.save(predmetCmd);
    }

}
