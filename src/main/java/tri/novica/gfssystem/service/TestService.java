package tri.novica.gfssystem.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.TipTesta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.TipTestaRepository;

@Service
@Transactional
@RequiredArgsConstructor()
public class TestService {

    private final TipTestaRepository tipTestaRepository;
    private final PredmetRepository predmetRepository;
    private final ModelMapper mapper;

    public TipTestaInfo createTipTesta(CreateTipTestaCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));

        TipTesta tipTesta = mapper.map(cmd, TipTesta.class);
        tipTesta.setPredmet(predmet);
        tipTesta.setAktivan(true);

        return mapper.map(tipTestaRepository.save(tipTesta), TipTestaInfo.class);
    }
}
