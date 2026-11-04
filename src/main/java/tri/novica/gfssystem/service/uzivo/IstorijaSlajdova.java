package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdIzmenaInfo;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajda;
import tri.novica.gfssystem.entity.uzivo.Pitanje;
import tri.novica.gfssystem.entity.uzivo.PitanjeOpcija;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.SlajdIzmena;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.entity.uzivo.VrstaIzmene;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.portal.PrikazaniSlajdoviRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdIzmenaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Istorija Klodovih izmena slajdova ({@code slajd_izmene}): snimak stanja pre izmene, čitanje i čišćenje. Upisuje samo
 * {@link PrezentacijaService}, pod ključem prezentacije; nastavnikove izmene u editoru se ovde ne beleže. Snimak je JSON
 * {@link SlajdCmd}, isti oblik koji {@code PUT /slajdovi/{id}} prima, pa se vraćanje radi kroz istu proveru kao svaki upis.
 */
@Component
@RequiredArgsConstructor
public class IstorijaSlajdova {

    static final String NIJE_PRONADJENA = "Izmena nije pronađena.";
    /** Najviše unosa po pozivu i po slajdu; unosi brisanja se ne čiste. */
    private static final int MAX_NA_STRANI = 50;

    private final SlajdIzmenaRepository repository;
    private final SlajdRepository slajdRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final PrikazaniSlajdoviRepository prikazaniRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Value("${gfs.istorija.po-slajdu:50}")
    private int poSlajdu = MAX_NA_STRANI;

    /** Sadržaj slajda kao komanda: PLATNO doslovno iz kolone, sa potezima; polja van tipa su {@code null} kao u {@code SlajdPP}. */
    SlajdCmd uCmd(Slajd s) {
        JsonNode platno = s.getPlatno() == null ? null : jsonMapper.readTree(s.getPlatno());
        ZadatakSlajda zadatak = s.getTip() != TipSlajda.ZADATAK ? null : new ZadatakSlajda(
                s.getGrupaZadataka() == null ? null : s.getGrupaZadataka().getId(), s.getZadatakVremeSekunde(),
                s.getZadatakBrojVarijanti(), s.getZadatakNivo());
        return new SlajdCmd(s.getTip(), s.getNaslov(), s.getSadrzaj(), s.getSlika() == null ? null : s.getSlika().getId(),
                s.getBeleske(), s.isPostepeno(), uCmd(s.getPitanje()), platno, SlajdMapper.uPrelaz(s.getPrelaz()), zadatak);
    }

    private static PitanjeCmd uCmd(Pitanje p) {
        if (p == null) {
            return null;
        }
        List<OpcijaCmd> opcije = p.getOpcije().stream()
                .sorted(Comparator.comparingInt(PitanjeOpcija::getRb))
                .map(o -> new OpcijaCmd(o.getTekst(), o.isTacna()))
                .toList();
        return new PitanjeCmd(p.getTip(), p.getTekst(), p.getSlika() == null ? null : p.getSlika().getId(), p.getVremeSekunde(),
                opcije, p.getBrojTacno(), p.getBrojOdstupanje(), p.getOdstupanjeTip(), p.getJedinica(), p.getTekstPrikaz(),
                p.getPrihvatljiviOdgovori() == null ? null : new ArrayList<>(p.getPrihvatljiviOdgovori()), p.getSkalaMinOznaka(),
                p.getSkalaMaxOznaka());
    }

    /** Isti sadržaj posle normalizacije: Klodova izmena tada ništa ne upisuje. */
    boolean isti(SlajdCmd a, SlajdCmd b) {
        return jsonMapper.valueToTree(a).equals(jsonMapper.valueToTree(b));
    }

    /** Upis stanja pre izmene; {@code pre} je {@code null} kad se slajd obnavlja (pre toga nije postojao). Vraća id unosa. */
    Long upisi(Long prezentacijaId, Slajd s, SlajdCmd pre, VrstaIzmene vrsta, String opis, String serija) {
        SlajdIzmena i = new SlajdIzmena();
        i.setPrezentacijaId(prezentacijaId);
        i.setSlajdId(s.getId());
        i.setRb(s.getRb());
        i.setVrsta(vrsta);
        i.setSerija(serija);
        i.setOpis(opis);
        i.setVerzijaPre(pre == null ? null : s.getVerzija());
        i.setSnimak(pre == null ? null : jsonMapper.writeValueAsString(pre));
        i.setKreirano(LocalDateTime.now(clock));
        Long id = repository.save(i).getId();
        ocisti(s.getId());
        return id;
    }

    private void ocisti(Long slajdId) {
        List<Long> visak = repository.idsZaCiscenje(slajdId, VrstaIzmene.BRISANJE, PageRequest.of(1, poSlajdu));
        if (!visak.isEmpty()) {
            repository.deleteAllById(visak);
        }
    }

    SlajdIzmena nadji(Long izmenaId) {
        return repository.findById(izmenaId)
                .orElseThrow(() -> new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
    }

    SlajdCmd snimak(SlajdIzmena i) {
        return jsonMapper.readValue(i.getSnimak(), SlajdCmd.class);
    }

    void preusmeri(Long stari, Long novi) {
        repository.preusmeri(stari, novi);
    }

    /** Unosi slajda, najnoviji prvi, i kad je slajd obrisan; bez ijednog unosa je prazno. */
    List<SlajdIzmenaInfo> slajda(Long slajdId) {
        return info(repository.findBySlajdIdOrderByIdDesc(slajdId, PageRequest.of(0, MAX_NA_STRANI)));
    }

    /** Najnoviji unosi prezentacije, sa slajdovima koji više ne postoje. */
    List<SlajdIzmenaInfo> prezentacije(Long prezentacijaId) {
        return info(repository.findByPrezentacijaIdOrderByIdDesc(prezentacijaId, PageRequest.of(0, MAX_NA_STRANI)));
    }

    private List<SlajdIzmenaInfo> info(List<SlajdIzmena> unosi) {
        Set<Long> ids = new HashSet<>();
        unosi.forEach(i -> ids.add(i.getSlajdId()));
        Map<Long, Boolean> postoji = new HashMap<>();
        ids.forEach(id -> postoji.put(id, slajdRepository.existsById(id)));
        return unosi.stream().map(i -> new SlajdIzmenaInfo(i.getId(), i.getPrezentacijaId(), i.getSlajdId(), i.getRb(), i.getVrsta(),
                i.getSerija(), i.getOpis(), i.getKreirano(), i.getSnimak() != null, !postoji.get(i.getSlajdId()))).toList();
    }

    /**
     * Klod čita PLATNO bez poteza crteža, pa ih ni ne šalje nazad: CRTEZ sloj bez {@code potezi} dobija sačuvane poteze sloja
     * istog id-ja, ako mu veličina nije promenjena (potezi su u koordinatama sloja). Promenjen ili nov crtež ostaje bez poteza,
     * prikazuje se kao slika i o tome ide upozorenje.
     */
    JsonNode saPotezima(JsonNode novi, JsonNode stari, List<String> upozorenja) {
        if (novi == null || !novi.isObject() || !novi.path("slojevi").isArray()) {
            return novi;
        }
        Map<String, JsonNode> sacuvani = new HashMap<>();
        if (stari != null) {
            stari.path("slojevi").forEach(s -> {
                if ("CRTEZ".equals(s.path("tip").asString(""))) {
                    sacuvani.put(s.path("id").asString(""), s);
                }
            });
        }
        ObjectNode dokument = (ObjectNode) novi.deepCopy();
        ArrayNode slojevi = (ArrayNode) dokument.get("slojevi");
        for (JsonNode sloj : slojevi) {
            if (!sloj.isObject() || !"CRTEZ".equals(sloj.path("tip").asString("")) || !sloj.path("potezi").isMissingNode()
                    && !sloj.path("potezi").isNull()) {
                continue;
            }
            String id = sloj.path("id").asString("");
            JsonNode s = sacuvani.get(id);
            boolean istaVelicina = s != null && istiBroj(s.path("sirina"), sloj.path("sirina"))
                    && istiBroj(s.path("visina"), sloj.path("visina"));
            if (istaVelicina && !s.path("potezi").isMissingNode()) {
                ((ObjectNode) sloj).set("potezi", s.get("potezi"));
            } else {
                upozorenja.add("Crtež '" + id + "' nema poteze; prikazuje se kao slika.");
            }
        }
        return dokument;
    }

    /** {@code 300} i {@code 300.0} su ista veličina: Jackson ih kao čvorove razlikuje, a klijent u JavaScript-u šalje ceo broj. */
    private static boolean istiBroj(JsonNode a, JsonNode b) {
        return a.isNumber() && b.isNumber() && a.doubleValue() == b.doubleValue();
    }

    /** Slajd ima runde ili je prikazan grupi: brisanje gubi tu vezu, a {@code vrati_izmenu} je ne obnavlja. */
    boolean koriscen(Long slajdId) {
        return rundaRepository.existsBySlajdId(slajdId) || prikazaniRepository.existsBySlajdId(slajdId);
    }
}
