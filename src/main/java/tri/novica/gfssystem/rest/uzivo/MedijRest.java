package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.service.uzivo.MedijService;

/** Nastavnički upload slika za slajdove i pitanja (iza basic-auth-a). Serviranje je javno: {@link PublicMedijRest}. */
@RestController
@RequiredArgsConstructor
public class MedijRest {

    private final MedijService medijService;

    @PostMapping(path = "/mediji", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public MedijInfo sacuvaj(@RequestParam("fajl") MultipartFile fajl) {
        return medijService.sacuvaj(fajl);
    }
}
