package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.spec.BankaSpecs;

import java.util.Set;

/**
 * Pretraga varijanti cele banke (spec banke 2.6): filteri po predmetu, nameni, statusu, izvoru, Klodovoj seriji (spec MCP
 * 6.1) i tekstu upita, stranica kao u ostalim listama (vidi "List and overview API conventions"). Čitanje: jedan upit za
 * stranicu i jedan za ukupan broj; grupa varijante i lanac njenih predaka dolaze u istom upitu ({@link BankaSpecs#zaPrikaz()}),
 * pa putanja ne košta ništa više.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankaPretragaService {

    public static final Set<String> SORT_POLJA = Set.of("izmenjeno", "naziv", "nivo");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("izmenjeno"));

    static final int DUZINA_ISECKA = 160;

    private final VarijantaRepository varijantaRepository;
    private final GrupaZadatakaService grupaZadatakaService;

    /** {@code pageable} je već prošao {@code PageableUtil.proveri} (REST sloj), {@code f} je ispravan po konstrukciji. */
    public PagedModel<BankaPretragaStavka> pretraga(BankaFilter f, Pageable pageable) {
        Specification<Varijanta> spec = Specification.allOf(
                BankaSpecs.zaPrikaz(),   // prvi: ostali filteri dele njegov join grupe
                BankaSpecs.predmet(f.predmetId()),
                BankaSpecs.namena(f.namena()),
                BankaSpecs.status(f.status()),
                BankaSpecs.izvor(f.izvor()),
                BankaSpecs.serija(f.serija()),
                BankaSpecs.nivo(f.nivo()),
                BankaSpecs.upit(f.q()));
        Page<Varijanta> strana = varijantaRepository.findAll(spec, pageable);
        return new PagedModel<>(strana.map(this::stavka));
    }

    private BankaPretragaStavka stavka(Varijanta v) {
        return new BankaPretragaStavka(v.getId(), v.getGrupa().getId(), v.getGrupa().getNaziv(),
                grupaZadatakaService.putanja(v.getGrupa()), v.getNaziv(), v.getStatus(), v.getRenderStatus(),
                isecak(v.getTekst()), v.getIzvor(), v.getSerija(), v.getNivo());
    }

    /** Prvih {@value #DUZINA_ISECKA} znakova (code point-a, bez cepanja para) sa novim redovima sažetim u razmak. */
    static String isecak(String tekst) {
        if (tekst == null) return "";
        String jedanRed = tekst.strip().replaceAll("\\s*\\R\\s*", " ");
        if (jedanRed.codePointCount(0, jedanRed.length()) <= DUZINA_ISECKA) return jedanRed;
        return jedanRed.substring(0, jedanRed.offsetByCodePoints(0, DUZINA_ISECKA));
    }
}
