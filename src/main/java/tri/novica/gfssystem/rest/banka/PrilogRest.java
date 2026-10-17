package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.service.uzivo.MedijService;

/**
 * Upload priloga zadatka banke (PNG, JPEG, PDF) za LaTeX render, iza basic-auth-a. Ovo je jedina putanja kojom PDF ulazi
 * u {@code mediji}: {@code POST /mediji} ostaje samo za slike. PDF prilog se javno ne servira ({@code PublicMedijRest}).
 */
@RestController
@RequestMapping("/banka/prilozi")
@RequiredArgsConstructor
public class PrilogRest {

    private final MedijService medijService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MedijInfo sacuvaj(@RequestParam("fajl") MultipartFile fajl) {
        return medijService.sacuvajPrilog(fajl);
    }
}
