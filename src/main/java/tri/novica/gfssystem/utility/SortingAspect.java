package tri.novica.gfssystem.utility;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.dto.test.TestDetails;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

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
                        Comparator.comparingInt(a -> index2int(a.getStudent().getIndeks()))
                );
    }

    @Pointcut("execution(public tri.novica.gfssystem.dto.test.TestDetails tri.novica.gfssystem.service.TestService.*(..))")
    public void vracenoTestDetails(){}

    @AfterReturning(pointcut = "vracenoTestDetails()", returning = "test")
    public void sortTest(JoinPoint jp, TestDetails test){
        test.setPolaganja(test.getPolaganja()
                .stream()
                .sorted(
                        Comparator.comparingInt(p -> index2int(p.getStudent().getIndeks()))
                ).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

}
