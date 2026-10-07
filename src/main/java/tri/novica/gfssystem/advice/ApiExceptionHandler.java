package tri.novica.gfssystem.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    /**
     * Tekst izuzetka (može sadržati SQL ili putanje) ide samo u log, klijent dobija opštu poruku. Springovi izuzeci sa
     * sopstvenim statusom (npr. 406 za neprihvatljiv Accept) zadržavaju taj status i ne loguju se kao ERROR.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiException> handleUnexpectedErrors(Exception ex){
        if (ex instanceof ErrorResponse) {
            return handleSpringGreske(ex);
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(new ApiException("Sistemska greška.", LocalDateTime.now()));
    }

    @ExceptionHandler(SystemException.class)
    ResponseEntity<ApiException> handleSystemExceptions(SystemException ex){
        Integer code = ex.getCode();
        code = code == null ? 400 : code;
        ApiException apiException =  new ApiException(ex.getMessage(), LocalDateTime.now());

        log.info("Exception handled. Code: {}, Reason: {}", ex.getCode(), ex.getMessage(), ex);

        return ResponseEntity.status(code).body(apiException);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiException> handleInvalidArgument(MethodArgumentNotValidException ex) {
        List<String> errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage) // Samo poruka greške
                .toList();

        String userFriendlyMessage = String.join(", ", errorMessages);

        ApiException apiException = new ApiException(userFriendlyMessage, LocalDateTime.now());

        // samo poruke, bez izuzetka: on nosi odbijene vrednosti (email, telefon, ime), a log se čuva 30 dana
        log.info("Invalid arguments: {}", userFriendlyMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiException);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiException> handleNecitljivZahtev(HttpMessageNotReadableException ex) {
        log.info("Neispravan zahtev: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.badRequest().body(new ApiException("Neispravan format podataka.", LocalDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiException> handlePogresanParametar(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ApiException("Neispravan parametar: " + ex.getName() + ".", LocalDateTime.now()));
    }

    /** Spring izuzeci sa sopstvenim statusom (nepostojeća putanja 404, pogrešna metoda 405, ...), umesto 500. */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiException> handleSpringGreske(Exception ex) {
        ErrorResponse greska = (ErrorResponse) ex;
        HttpStatusCode status = greska.getStatusCode();
        String razlog;
        if (status.is5xxServerError()) {
            log.error("Unexpected error", ex);
            razlog = "Sistemska greška.";
        } else {
            razlog = status.value() == 404 ? "Ne postoji." : "Neispravan zahtev.";
        }
        // zaglavlja koja Spring sam postavlja (Allow za 405, Accept za 415)
        return ResponseEntity.status(status).headers(greska.getHeaders()).body(new ApiException(razlog, LocalDateTime.now()));
    }
}
