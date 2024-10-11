package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.predavanje.PredavanjeInfo;
import tri.novica.gfssystem.dto.predavanje.StartPredavanjeCmd;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PredavanjeService {

    private final PredavanjeRepository predavanjeRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final ModelMapper mapper;

    public PredavanjeInfo startPredavanje(StartPredavanjeCmd startPredavanjeCmd) {

        Long predmetId = startPredavanjeCmd.getPredmetId();
        Long grupaId = startPredavanjeCmd.getGrupaId();

        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet nije nadjen! ID = " + predmetId, HttpStatus.NOT_FOUND.value()));
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa nije pronadjena! ID = " + grupaId, HttpStatus.NOT_FOUND.value()));

        Predavanje predavanje = new Predavanje();
        predavanje.setPredmet(predmet);
        predavanje.setGrupa(grupa);
        predavanje.setDatum(LocalDate.now());

        Integer redniBroj = predavanjeRepository.findPoslednjiRB();
        if (redniBroj == null || redniBroj == 0) redniBroj = 1;
        else redniBroj++;
        predavanje.setRb(redniBroj);

        return mapper.map(predavanjeRepository.save(predavanje), PredavanjeInfo.class);
    }
}
