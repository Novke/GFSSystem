package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.domaci.DodajDomaciCmd;
import tri.novica.gfssystem.dto.domaci.DomaciInfo;
import tri.novica.gfssystem.entity.Domaci;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.DomaciRepository;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DomaciService {

    private final DomaciRepository domaciRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final GrupaRepository grupaRepository;
    private final PredmetRepository predmetRepository;
    private final ModelMapper mapper;


    public DomaciInfo dodajDomaci(DodajDomaciCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));
        Grupa grupa = grupaRepository.findById(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.getGrupaId(), 404));

        Long predavanjeId = cmd.getPredavanjeId();
        Predavanje predavanje = null;
        if (predavanjeId != null && predavanjeId != 0){
            predavanje = predavanjeRepository.findById(predavanjeId)
                    .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, 404));
        }

        Domaci domaci = new Domaci();
        domaci.setPredavanje(predavanje);
        domaci.setPredmet(predmet);
        domaci.setGrupa(grupa);
        domaci.setDatum(LocalDate.now());

        return mapper.map(domaciRepository.save(domaci), DomaciInfo.class);
    }
}
