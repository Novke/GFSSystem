package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.aktivnost.UpdateAktivnostNapomenaCmd;
import tri.novica.gfssystem.dto.predavanje.*;
import tri.novica.gfssystem.service.PredavanjeService;

import java.util.List;

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

    @PutMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails update(@PathVariable Long id, @RequestBody UpdatePredavanjeCmd updatePredavanjeCmd){
        return predavanjeService.updatePredavanje(id, updatePredavanjeCmd);
    }

    @PutMapping("/{id}/posecenost")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails updatePosecenost(@PathVariable Long id){
        return predavanjeService.updatePosecenost(id);
    }


    @PatchMapping("/{id}/prisustvo")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails dodajPrisutnog(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.dodajPrisutnog(id, studentId.getId());
    }

    @DeleteMapping("/{pId}/prisustvo/{sId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails skloniPrisutnog(@PathVariable Long pId, @PathVariable Long sId){
        return predavanjeService.skloniPrisutnog(pId, sId);
    }


    @PatchMapping("/{id}/zadatak")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails dodajZadatak(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.dodajZadatak(id, studentId.getId());
    }

    @DeleteMapping("/{pId}/zadatak/{sId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails skloniZadatak(@PathVariable Long pId, @PathVariable Long sId){
        return predavanjeService.skloniZadatak(pId, sId);
    }

    @PatchMapping("/{id}/zvezdica")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeDetails dodajZadatakSaZvezdicom(@PathVariable Long id, @RequestBody PredavanjeStudentId studentId){
        return predavanjeService.dodajZadatakSaZvezdicom(id, studentId.getId());
    }

    @PutMapping("/aktivnost/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PredavanjeAktivnostInfo updateNapomenu(@PathVariable Long id, @RequestBody UpdateAktivnostNapomenaCmd cmd){
        return predavanjeService.updateAktivnostNapomenu(id, cmd);
    }

    @GetMapping("/search")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<PredavanjeInfo> search(@RequestParam Long predmet, @RequestParam Long grupa){
        return predavanjeService.pretraziPredavanja(predmet, grupa);
    }
}
