package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.grupa.CreateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.GrupaDetails;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.grupa.KontaktiFajl;
import tri.novica.gfssystem.dto.grupa.UpdateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.WaLinkCmd;
import tri.novica.gfssystem.dto.grupa.WaLinkInfo;
import tri.novica.gfssystem.dto.pregled.GrupaPregledInfo;
import tri.novica.gfssystem.dto.pregled.PrisustvoMatricaInfo;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.uzivo.UzivoGrupe;
import tri.novica.gfssystem.service.GrupaService;
import tri.novica.gfssystem.service.PregledGrupeService;
import tri.novica.gfssystem.service.StudentService;
import tri.novica.gfssystem.service.uzivo.RangTacnostiService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/grupe")
@RequiredArgsConstructor
public class GrupaRest {

    private static final MediaType VCARD = new MediaType("text", "vcard", StandardCharsets.UTF_8);

    private final GrupaService grupaService;
    private final StudentService studentService;
    private final PregledGrupeService pregledGrupeService;
    private final RangTacnostiService rangTacnostiService;

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
    public GrupaInfo createGrupa(@Valid @RequestBody CreateGrupaCmd grupaCmd){
        return grupaService.save(grupaCmd);
    }

    @PutMapping("{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public GrupaInfo updateGrupa(@PathVariable Long id, @Valid @RequestBody UpdateGrupaCmd grupaCmd){
        return grupaService.update(id, grupaCmd);
    }

    @GetMapping("/{id}/studenti")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<StudentInfo> findAllStudents(@PathVariable Long id){
        return studentService.findAllByGroup(id);
    }

    /** Rang tačnosti studenata grupe kroz Uživo izvođenja; filteri opcioni (predmet, školska godina). */
    @GetMapping("/{id}/uzivo")
    @ResponseStatus(HttpStatus.OK)
    public UzivoGrupe uzivo(@PathVariable Long id, @RequestParam(required = false) Long predmetId,
                            @RequestParam(required = false) Integer godina) {
        return rangTacnostiService.zaGrupu(id, predmetId, godina);
    }

    /** G1 + G2: brojke grupe i statistika po studentu; bez {@code predmetId} preko svih predmeta. */
    @GetMapping("/{id}/pregled")
    @ResponseStatus(HttpStatus.OK)
    public GrupaPregledInfo pregled(@PathVariable Long id, @RequestParam(required = false) Long predmetId) {
        return pregledGrupeService.pregled(id, predmetId);
    }

    /**
     * G3: matrica prisustva. {@code predmetId} je obavezan, ali se proverava u servisu da bi poruka bila
     * "Neispravan parametar: predmetId." (kao za pogrešan tip), a ne opšta.
     */
    @GetMapping("/{id}/prisustvo")
    @ResponseStatus(HttpStatus.OK)
    public PrisustvoMatricaInfo prisustvo(@PathVariable Long id, @RequestParam(required = false) Long predmetId,
                                          @RequestParam(required = false) Integer godina) {
        return pregledGrupeService.prisustvo(id, predmetId, godina);
    }

    /** Link za poziv u WhatsApp grupu: telo {@code {"waLink": "<link>"|null}}; null ili prazno uklanja link. */
    @PatchMapping("/{id}/wa-link")
    @ResponseStatus(HttpStatus.OK)
    public WaLinkInfo postaviWaLink(@PathVariable Long id, @Valid @RequestBody WaLinkCmd cmd) {
        return grupaService.postaviWaLink(id, cmd);
    }

    /**
     * Kontakti grupe kao vCard za uvoz u telefon (Android: Kontakti / Google Contacts, iPhone: Safari). Iza basic-auth-a
     * kao i ostatak {@code /grupe}: sadrži brojeve telefona i emailove, nikad pod {@code /public}.
     */
    @GetMapping("/{id}/kontakti.vcf")
    public ResponseEntity<byte[]> kontakti(@PathVariable Long id) {
        KontaktiFajl fajl = grupaService.kontakti(id);
        return ResponseEntity.ok()
                .contentType(VCARD)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fajl.imeFajla(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(fajl.sadrzaj().getBytes(StandardCharsets.UTF_8));
    }
}
