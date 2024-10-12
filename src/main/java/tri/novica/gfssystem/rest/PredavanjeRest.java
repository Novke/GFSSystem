package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.dto.predavanje.PredavanjeStudentId;
import tri.novica.gfssystem.dto.predavanje.StartPredavanjeCmd;
import tri.novica.gfssystem.service.PredavanjeService;

@RestController
@RequestMapping("/predavanja")
@RequiredArgsConstructor
public class PredavanjeRest {

    private final PredavanjeService predavanjeService;

    @GetMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails view(@PathVariable Long id){
        return predavanjeService.findById(id);
    }

    @PostMapping("/start")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public PredavanjeDetails start(@RequestBody StartPredavanjeCmd startPredavanjeCmd){
        return predavanjeService.startPredavanje(startPredavanjeCmd);
    }


    @PatchMapping("/{id}/prisustvo")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails dodajPrisutnog(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.dodajPrisutnog(id, studentId.getId());
    }

    @DeleteMapping("/{id}/prisustvo")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails skloniPrisutnog(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.skloniPrisutnog(id, studentId.getId());
    }


    @PatchMapping("/{id}/zadatak")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails dodajZadatak(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.dodajZadatak(id, studentId.getId());
    }

    @DeleteMapping("/{id}/zadatak")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails skloniZadatak(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.skloniZadatak(id, studentId.getId());
    }
}
