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
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.utility.Utility;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupaService {

    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final ModelMapper mapper;

    public List<GrupaInfo> findAll() {
        return grupaRepository.findAll().stream()
                .map(grupa -> {
                    GrupaInfo info = mapper.map(grupa, GrupaInfo.class);
                    info.setBrojStudenata(studentRepository.countByGrupaId(grupa.getId()));
                    return info;
                }).toList();
    }

    public GrupaInfo save(CreateGrupaCmd grupaCmd){
        String naziv = grupaCmd.getNaziv() == null ? null : grupaCmd.getNaziv().trim();
        if (naziv != null && grupaRepository.existsByNazivIgnoreCase(naziv)) {
            throw new SystemException("Grupa sa nazivom " + naziv + " već postoji.", HttpStatus.BAD_REQUEST);
        }
        Grupa grupa = mapper.map(grupaCmd, Grupa.class);
        grupa.setNaziv(naziv);
        return mapper.map(grupaRepository.save(grupa), GrupaInfo.class);
    }

    public GrupaDetails findById(Long id) {
        //moze i findById jer ce zbog mappera pozvati getStudenti ali je ovako bolje zbog efikasnijeg upita
        Grupa grupa = grupaRepository.findByIdFetchStudents(id).orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        //sortiraj po indeksu
        grupa.getStudenti().sort(
                Comparator.comparingInt(student ->
                        Utility.index2int(student.getIndeks())));

        return mapper.map(grupa, GrupaDetails.class);
    }
}
