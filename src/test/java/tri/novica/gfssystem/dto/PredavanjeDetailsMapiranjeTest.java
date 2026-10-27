package tri.novica.gfssystem.dto;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Detalji predavanja nose id predmeta (front po njemu bira prezentacije za predavanje), uz isti STRICT mapper kao u aplikaciji. */
class PredavanjeDetailsMapiranjeTest {

    @Test
    void predmetImaIdINaziv() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setPropertyCondition(context -> context.getSource() != null)
                .setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        Predmet predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Statika");
        Grupa grupa = new Grupa();
        grupa.setId(3L);
        grupa.setNaziv("GD-2025");
        Predavanje p = new Predavanje();
        p.setId(40L);
        p.setPredmet(predmet);
        p.setGrupa(grupa);
        p.setRb(2);

        PredavanjeDetails d = mapper.map(p, PredavanjeDetails.class);

        assertEquals(7L, d.getPredmet().getId());
        assertEquals("Statika", d.getPredmet().getNaziv());
        assertEquals(3L, d.getGrupa().getId());
    }
}
