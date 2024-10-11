package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.predavanje.PredavanjeInfo;
import tri.novica.gfssystem.dto.predavanje.StartPredavanjeCmd;
import tri.novica.gfssystem.service.PredavanjeService;

@RestController
@RequestMapping("/predavanja")
@RequiredArgsConstructor
public class PredavanjeRest {

    private final PredavanjeService predavanjeService;

    @PostMapping("/start")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public PredavanjeInfo start(@RequestBody StartPredavanjeCmd startPredavanjeCmd){
        return predavanjeService.startPredavanje(startPredavanjeCmd);
    }

}
