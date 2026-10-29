package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajdaInfo;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.utility.Nivo;

import java.util.*;

/**
 * Opis Zadatak slajdova (editor, konzola, MCP): ono što je na slajdu plus čvor banke u trenutku čitanja (putanja, šema,
 * broj kandidata, isključene podgrupe, SVG jednog primera) i upozorenja za Novicu. Isti izvor ({@link KandidatiZadatka}) kao izvlačenje pri
 * otvaranju runde, pa se editor i čas ne razilaze. Batch ({@link #za(Long, Collection)}) čita stablo predmeta jednom za
 * sve Zadatak slajdove, i uopšte samo kad ima ZADATAK slajda sa grupom. Samo čitanje.
 * <p>
 * Upozorenja, ovim redom: {@value #OBRISANA} (samo ono, kad grupe nema), {@value #ISKLJUCENA_TEMA} (čvor ili predak),
 * {@value #BEZ_AKTIVNIH} (nula kandidata) ili {@value #BEZ_AKTIVNIH_NIVOA} (ima kandidata, ali nijednog nivoa slajda), pa
 * spisak isključenih podgrupa. Grupa koje nema u stablu PREDAVANJE predmeta
 * (obrisana, ili iz drugog predmeta ili namene TEST) računa se kao obrisana: slajd je bez upotrebljive grupe.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ZadaciSlajdova {

    static final String OBRISANA = "Grupa zadataka je obrisana; izaberi drugu.";
    static final String ISKLJUCENA_TEMA = "Tema je isključena.";
    static final String BEZ_AKTIVNIH = "Grupa nema aktivnih varijanti; odobri nacrte u banci pre časa.";
    static final String ISKLJUCENE_PODGRUPE = "Isključene podgrupe se ne izvlače: ";
    /** {@code %s} = prisutni nivoi kao zvezdice, npr. {@code ★, ★★}. */
    static final String NIVO_NIJE_IZABRAN = "Nivo nije izabran, a grupa ima varijante nivoa %s: pri svakom otvaranju "
            + "izvlači se jedan nivo nasumično (svi u rundi dobiju isti), pa je zadatak nekad lakši, nekad teži. Izaberi nivo.";
    /** {@code %s} = nivo kao zvezdice ({@code Nivo.oznaka}). */
    static final String BEZ_AKTIVNIH_NIVOA = "Nema aktivnih varijanti nivoa %s; odobri nacrte ili izaberi drugi nivo.";
    private static final List<Integer> BEZ_KANDIDATA = List.of(0, 0, 0);

    private final KandidatiZadatka kandidati;

    /**
     * Opisi svih ZADATAK slajdova iz {@code slajdovi}, po id-ju slajda (ostali tipovi nemaju stavku); jedno čitanje
     * stabla predmeta {@code predmetId} samo kad bar jedan od njih ima grupu, inače nijedan upit.
     */
    public Map<Long, ZadatakSlajdaInfo> za(Long predmetId, Collection<Slajd> slajdovi) {
        List<Slajd> zadaci = slajdovi.stream().filter(s -> s.getTip() == TipSlajda.ZADATAK).toList();
        if (zadaci.isEmpty()) {
            return Map.of();
        }
        StabloPredavanja stablo = zadaci.stream().anyMatch(s -> s.getGrupaZadataka() != null)
                ? kandidati.stablo(predmetId) : null;
        return opisi(zadaci, stablo);
    }

    /** Opis jednog slajda, {@code null} za slajd koji nije ZADATAK. Za više slajdova istog predmeta {@link #za(Long, Collection)}. */
    public ZadatakSlajdaInfo za(Slajd s) {
        if (s.getTip() != TipSlajda.ZADATAK) {
            return null;
        }
        StabloPredavanja stablo = s.getGrupaZadataka() == null ? null
                : kandidati.stablo(s.getPrezentacija().getPredmet().getId());
        return opisi(List.of(s), stablo).get(s.getId());
    }

    /** Opisi po id-ju slajda; SVG primera svih slajdova jednim upitom (samo kad ima primera). */
    private Map<Long, ZadatakSlajdaInfo> opisi(List<Slajd> zadaci, StabloPredavanja stablo) {
        Map<Long, Long> primeri = new HashMap<>();
        for (Slajd s : zadaci) {
            Long primer = stablo == null || s.getGrupaZadataka() == null ? null
                    : stablo.primer(stablo.opis(s.getGrupaZadataka().getId()), s.getZadatakNivo());
            if (primer != null) {
                primeri.put(s.getId(), primer);
            }
        }
        Map<Long, String> svg = kandidati.svgTeksta(new HashSet<>(primeri.values()));
        Map<Long, ZadatakSlajdaInfo> opisi = new HashMap<>();
        for (Slajd s : zadaci) {
            Long primer = primeri.get(s.getId());
            opisi.put(s.getId(), info(s, stablo, primer == null ? null : svg.get(primer)));
        }
        return opisi;
    }

    private static ZadatakSlajdaInfo info(Slajd s, StabloPredavanja stablo, String primerSvgId) {
        GrupaZadataka grupa = s.getGrupaZadataka();
        Opis opis = grupa == null || stablo == null ? null : stablo.opis(grupa.getId());
        if (opis == null) {
            return new ZadatakSlajdaInfo(null, s.getZadatakVremeSekunde(), s.getZadatakBrojVarijanti(), s.getZadatakNivo(),
                    null, List.of(), false, 0, BEZ_KANDIDATA, null, false, List.of(), List.of(OBRISANA), null);
        }
        List<String> upozorenja = new ArrayList<>();
        if (opis.iskljucena()) {
            upozorenja.add(ISKLJUCENA_TEMA);
        }
        Integer nivo = s.getZadatakNivo();
        if (opis.brojKandidata() == 0) {
            upozorenja.add(BEZ_AKTIVNIH);
        } else if (opis.brojKandidata(nivo) == 0) {
            upozorenja.add(BEZ_AKTIVNIH_NIVOA.formatted(Nivo.oznaka(nivo)));
        } else if (nivo == null) {
            List<String> nivoi = new ArrayList<>();
            for (int i = 0; i < opis.poNivou().size(); i++) {
                if (opis.poNivou().get(i) > 0) nivoi.add(Nivo.oznaka(i + 1));
            }
            if (nivoi.size() > 1) {
                upozorenja.add(NIVO_NIJE_IZABRAN.formatted(String.join(", ", nivoi)));
            }
        }
        if (!opis.iskljucenePodgrupe().isEmpty()) {
            upozorenja.add(ISKLJUCENE_PODGRUPE
                    + String.join(", ", opis.iskljucenePodgrupe().stream().map(GrupaPutanja::naziv).toList()) + ".");
        }
        return new ZadatakSlajdaInfo(opis.grupaId(), s.getZadatakVremeSekunde(), s.getZadatakBrojVarijanti(), nivo,
                opis.naziv(), opis.putanja(), opis.list(), opis.brojKandidata(nivo), opis.poNivou(), opis.polja(),
                opis.iskljucena(), opis.iskljucenePodgrupe(), List.copyOf(upozorenja), primerSvgId);
    }
}
