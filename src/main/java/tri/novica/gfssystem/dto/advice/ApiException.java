package tri.novica.gfssystem.dto.advice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiException {

    private String reason;
    private LocalDateTime time;

}
