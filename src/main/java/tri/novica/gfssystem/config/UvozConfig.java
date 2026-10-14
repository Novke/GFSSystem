package tri.novica.gfssystem.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tri.novica.gfssystem.exceptions.SystemException;

/**
 * Granica tela uvoza slajdova (spec 7.7): {@code Content-Length} > 10 MB -> 413 pre čitanja tela. Spring i Tomcat
 * nemaju granicu za JSON telo; zahtev bez {@code Content-Length} (chunked) zaustavlja nginx frontend kontejnera
 * ({@code client_max_body_size 11m}). Izuzetak iz {@code preHandle} obrađuje {@code ApiExceptionHandler}.
 */
@Configuration
public class UvozConfig implements WebMvcConfigurer {

    public static final long MAX_TELO_UVOZA = 10L * 1024 * 1024;
    public static final String PREVELIK_UVOZ = "Uvoz je prevelik (najviše 10 MB). Uvezi deo po deo (opseg slajdova).";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest zahtev, HttpServletResponse odgovor, Object handler) {
                if (zahtev.getContentLengthLong() > MAX_TELO_UVOZA) {
                    throw new SystemException(PREVELIK_UVOZ, HttpStatus.CONTENT_TOO_LARGE);
                }
                return true;
            }
        }).addPathPatterns("/prezentacije/*/slajdovi/uvoz");
    }
}
