package tri.novica.gfssystem.utility;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;

import static tri.novica.gfssystem.utility.Utility.index2int;

@Aspect
@Component
public class SortingAspect {

    @Pointcut("execution(public tri.novica.gfssystem.dto.predavanje.PredavanjeDetails tri.novica.gfssystem.service.PredavanjeService.*(..))")
    public void vracenoPredavanjeDetails(){}

    @AfterReturning(pointcut = "vracenoPredavanjeDetails()", returning = "predavanje")
    public void sortPredavanja(JoinPoint jp, PredavanjeDetails predavanje){
        predavanje.getAktivnosti()
                .sort(
                        (a1, a2) -> index2int(a1.getStudent().getIndeks())-index2int(a2.getStudent().getIndeks())
                );
    }

}
