package tri.novica.gfssystem.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.LocalDateTime;

/**
 * Greške uploada (multipart) kao 400 sa porukom za korisnika, u istom obliku kao {@link ApiExceptionHandler}.
 * Mora imati prednost: {@code ApiExceptionHandler} hvata svaki {@code Exception}, a od više advice-a pobeđuje prvi
 * po redosledu koji ima odgovarajući handler, ne najspecifičniji.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiException> prevelikFajl(MaxUploadSizeExceededException ex) {
        log.info("Upload odbijen: prevelik fajl");
        return ResponseEntity.badRequest().body(new ApiException("Slika je veća od 10 MB.", LocalDateTime.now()));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiException> neispravanUpload(MultipartException ex) {
        log.info("Upload odbijen: {}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(new ApiException("Neispravan upload.", LocalDateTime.now()));
    }
}
