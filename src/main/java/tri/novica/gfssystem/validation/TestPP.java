package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.exceptions.SystemException;

@Component
public class TestPP {

    public void checkCreate(Test test){
        if (!test.getTipTesta().getPredmet().getId().equals(test.getPredmet().getId()))
            throw new SystemException("Tip testa ne pripada odabranom predmetu!", HttpStatus.BAD_REQUEST);
        if (test.getMaxPoena()<=0)
            throw new SystemException("Max poena mora biti veci od 0!", HttpStatus.BAD_REQUEST);
        if (test.getGrupe() == null || test.getGrupe().isEmpty())
            throw new SystemException("Ne moze biti 0 grupa!", HttpStatus.BAD_REQUEST);
    }

}
