package tri.novica.gfssystem.validation;

import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class TestPP {

    public void checkCreateTest(Test test){
        if (!test.getTipTesta().getPredmet().getId().equals(test.getPredmet().getId()))
            throw new SystemException("Tip testa ne pripada odabranom predmetu!", BAD_REQUEST);
        if (test.getMaxPoena()<=0)
            throw new SystemException("Max poena mora biti veci od 0!", BAD_REQUEST);
        if (test.getGrupe() == null || test.getGrupe().isEmpty())
            throw new SystemException("Ne moze biti 0 grupa!", BAD_REQUEST);
        if (!test.getTipTesta().getAktivan()){
            throw new SystemException("Tip testa nije aktivan!", BAD_REQUEST);
        }
        if (test.getPregledan())
            throw new SystemException("Nemoguce je kreirati pregledan test!");
    }

    public void checkUpdateTest(Test test) {
        checkCreateTest(test);
        if (test.getPolaganja().stream().anyMatch(
                p -> p.getOstvareniPoeni() != null && p.getOstvareniPoeni() > test.getMaxPoena()
        )) throw new SystemException("Postoji student sa vecim brojem poena od novog maksimuma (" + test.getMaxPoena() + ")");
    }

    public void checkCreatePolaganje(Polaganje polaganje) {
        var test = polaganje.getTest();
        if (!test.getGrupe().contains(polaganje.getGrupa()))
            throw new SystemException("Grupa " + polaganje.getGrupa().name() + " ne postoji u testu!", BAD_REQUEST);
        if (polaganje.getOstvareniPoeni()> test.getMaxPoena())
            throw new SystemException("Ostvareni broj poena ne moze biti veci od maksimalnog broja poena na testu", BAD_REQUEST);
        if (!polaganje.getTest().getGrupa().equals(polaganje.getStudent().getGrupa())){
            throw new SystemException("Student " + polaganje.getStudent().getIndeks() + " ne pripada grupi " + polaganje.getTest().getGrupa().getNaziv(), BAD_REQUEST);
        }
        if (test.getPregledan())
            throw new SystemException("Test je oznacen kao pregledan!");
    }
    public void checkZavrsiEvidentiranje(Test test) {
        if (test.getPregledan())
            throw new SystemException("Test je vec evidentiran!", BAD_REQUEST);
        if (test.getPolaganja().stream().anyMatch(
                p -> p.getOstvareniPoeni() == null
        )) throw new SystemException("Nisu svi dodati studenti evidentirani!", BAD_REQUEST);
    }
}
