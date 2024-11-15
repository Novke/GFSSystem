package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.predmet.CreatePredmetCmd;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.TipTestaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PredmetService {

    private final PredmetRepository predmetRepository;
    private final TipTestaRepository tipTestaRepository;
    private final ModelMapper mapper;

    public List<PredmetInfo> findAll() {
        return predmetRepository.findAll()
                .stream().map(
                        predmet -> mapper.map(predmet, PredmetInfo.class)
                ).toList();
    }

    public PredmetInfo save(CreatePredmetCmd predmetCmd) {
        return mapper.map(predmetRepository.save(
                mapper.map(predmetCmd, Predmet.class)
        ), PredmetInfo.class);
    }

    public PredmetInfo findById(Long id) {
        return mapper.map(
                predmetRepository.findById(id)
                        .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + id, 404)),
                PredmetInfo.class
        );
    }

    public List<TipTestaInfo> findTipovePredmeta(Long id) {
        Predmet predmet = predmetRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + id, 404));

        return tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet)
                .stream().map(tip -> mapper.map(tip, TipTestaInfo.class)).toList();
    }
}
