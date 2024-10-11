package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.grupa.CreateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.GrupaDetails;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupaService {

    private final GrupaRepository grupaRepository;
    private final ModelMapper mapper;

    public List<GrupaInfo> findAll() {
        return grupaRepository.findAll().stream()
                .map(
                        grupa -> mapper.map(grupa, GrupaInfo.class)
                ).toList();
    }

    public GrupaInfo save(CreateGrupaCmd grupaCmd){
        return mapper.map(grupaRepository.save(
                mapper.map(grupaCmd, Grupa.class)
                ), GrupaInfo.class);
    }

    public GrupaDetails findById(Long id) {
        //moze i findById jer ce zbog mappera pozvati getStudenti ali je ovako bolje zbog efikasnijeg upita
        Grupa grupa = grupaRepository.findByIdFetchStudents(id).orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + id, HttpStatus.NOT_FOUND.value()));

        return mapper.map(grupa, GrupaDetails.class);
    }
}
