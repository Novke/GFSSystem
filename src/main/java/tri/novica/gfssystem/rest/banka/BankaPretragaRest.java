package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.service.banka.BankaPretragaService;
import tri.novica.gfssystem.utility.PageableUtil;

/** Pretraga varijanti cele banke (iza basic-auth-a, van {@code /public}). */
@RestController
@RequestMapping(path = "/banka", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BankaPretragaRest {

    private final BankaPretragaService pretragaService;

    /**
     * Lista varijanti: filteri su opcioni, {@code q} (bar 2 znaka, 400 inače) se traži u nazivu i tekstu varijante i u
     * nazivu grupe; {@code page}/{@code size} (podrazumevano 25, najviše 100) i {@code sort} po {@code izmenjeno} ili
     * {@code naziv} (podrazumevano {@code izmenjeno,desc}).
     */
    @GetMapping("/pretraga")
    @ResponseStatus(HttpStatus.OK)
    public PagedModel<BankaPretragaStavka> pretraga(@RequestParam(required = false) Long predmetId,
            @RequestParam(required = false) Namena namena, @RequestParam(required = false) String q,
            @RequestParam(required = false) StatusVarijante status, Pageable pageable) {
        return pretragaService.pretraga(new BankaFilter(predmetId, namena, q, status),
                PageableUtil.proveri(pageable, BankaPretragaService.SORT_POLJA, BankaPretragaService.PODRAZUMEVANI_SORT));
    }
}
