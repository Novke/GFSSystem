package tri.novica.gfssystem.utility;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledAktivnostInfo;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledDetails;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledDomaciInfo;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledTestInfo;
import tri.novica.gfssystem.entity.Aktivnost;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.UradjenDomaci;

import java.util.List;
import java.util.Set;

@Mapper
public interface StudentMapper {
    StudentMapper INSTANCE = Mappers.getMapper(StudentMapper.class);

    @Mapping(source = "grupa.naziv", target = "grupa")
    @Mapping(target = "aktivnosti", ignore = true)
    @Mapping(target = "polaganja", ignore = true)
    @Mapping(target = "uradjeniDomaci", ignore = true)
    StudentPregledDetails toPregledDetails(Student student);

    @Mapping(source = "predavanje.id", target = "predavanjeId")
    @Mapping(source = "predavanje.datum", target = "datum")
    @Mapping(source = "predavanje.tema", target = "tema")
    StudentPregledAktivnostInfo toAktivnostDetails(Aktivnost aktivnost);
    @Mapping(source = "predavanje.id", target = "predavanjeId")
    @Mapping(source = "predavanje.datum", target = "datum")
    @Mapping(source = "predavanje.tema", target = "tema")
    List<StudentPregledAktivnostInfo> toAktivnostDetails(Set<Aktivnost> aktivnosti);

    @Mapping(source = "domaci.id", target = "domaciId")
    @Mapping(source = "domaci.datum", target = "datum")
    @Mapping(source = "domaci.naslov", target = "naslov")
    StudentPregledDomaciInfo toDomaciDetails(UradjenDomaci domaci);

    @Mapping(source = "domaci.id", target = "domaciId")
    @Mapping(source = "domaci.datum", target = "datum")
    @Mapping(source = "domaci.naslov", target = "naslov")
    List<StudentPregledDomaciInfo> toDomaciDetails(Set<UradjenDomaci> domaci);

    @Mapping(source = "test.id", target = "testId")
    @Mapping(source = "test.datum", target = "datum")
    @Mapping(source = "test.tipTesta", target = "tipTesta")
    StudentPregledTestInfo toTestDetails(Polaganje polaganje);

    @Mapping(source = "test.id", target = "testId")
    @Mapping(source = "test.datum", target = "datum")
    @Mapping(source = "test.tipTesta", target = "tipTesta")
    List<StudentPregledTestInfo> toTestDetails(Set<Polaganje> polaganja);
}
