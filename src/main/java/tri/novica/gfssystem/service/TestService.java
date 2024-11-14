package tri.novica.gfssystem.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.test.CreateTestCmd;
import tri.novica.gfssystem.dto.test.TestInfo;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.entity.TipTesta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.TestRepository;
import tri.novica.gfssystem.repository.TipTestaRepository;
import tri.novica.gfssystem.validation.TestPP;

@Service
@Transactional
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final TipTestaRepository tipTestaRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final ModelMapper mapper;
    private final TestPP testPP;

    public TipTestaInfo createTipTesta(CreateTipTestaCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));

        TipTesta tipTesta = mapper.map(cmd, TipTesta.class);
        tipTesta.setPredmet(predmet);
        tipTesta.setAktivan(true);

        return mapper.map(tipTestaRepository.save(tipTesta), TipTestaInfo.class);
    }

    public TestInfo createTest(CreateTestCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));
        Grupa grupa = grupaRepository.findById(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.getGrupaId(), 404));
        TipTesta tipTesta = tipTestaRepository.findById(cmd.getTipTestaId())
                .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.getTipTestaId(), 404));

        Test test = mapper.map(cmd, Test.class);
        test.setPredmet(predmet);
        test.setGrupa(grupa);
        test.setTipTesta(tipTesta);
        test.setPregledan(false);

        testPP.checkCreate(test);

        return mapper.map(testRepository.save(test), TestInfo.class);
    }
}
