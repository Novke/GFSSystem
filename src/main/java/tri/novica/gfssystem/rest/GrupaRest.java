package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.grupa.CreateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.GrupaDetails;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.service.GrupaService;

import java.util.List;

@RestController
@RequestMapping("/grupe")
@RequiredArgsConstructor
public class GrupaRest {

    private final GrupaService grupaService;

    @GetMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<GrupaInfo> findall(){
        return grupaService.findAll();
    }

    @GetMapping("{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public GrupaDetails findById(@PathVariable Long id){
        return grupaService.findById(id);
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public GrupaInfo createGrupa(@RequestBody CreateGrupaCmd grupaCmd){
        return grupaService.save(grupaCmd);
    }

}
