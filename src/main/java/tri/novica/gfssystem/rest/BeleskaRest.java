package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.beleska.BeleskaInfo;
import tri.novica.gfssystem.dto.beleska.SaveBeleskaCmd;
import tri.novica.gfssystem.service.BeleskaService;

import java.util.List;

/** Beleške nastavnika o studentu. Namerno van {@code /public/**}: ostaje iza basic-auth. */
@RestController
@RequiredArgsConstructor
public class BeleskaRest {

    private final BeleskaService beleskaService;

    @GetMapping("/studenti/{studentId}/beleske")
    @ResponseStatus(HttpStatus.OK)
    public List<BeleskaInfo> findByStudent(@PathVariable Long studentId) {
        return beleskaService.findByStudent(studentId);
    }

    @PostMapping("/studenti/{studentId}/beleske")
    @ResponseStatus(HttpStatus.CREATED)
    public BeleskaInfo create(@PathVariable Long studentId, @Valid @RequestBody SaveBeleskaCmd cmd) {
        return beleskaService.create(studentId, cmd);
    }

    @PutMapping("/beleske/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BeleskaInfo update(@PathVariable Long id, @Valid @RequestBody SaveBeleskaCmd cmd) {
        return beleskaService.update(id, cmd);
    }

    @DeleteMapping("/beleske/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        beleskaService.delete(id);
    }
}
