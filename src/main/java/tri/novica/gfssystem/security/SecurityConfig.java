package tri.novica.gfssystem.security;

import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.utility.KlijentIp;

import java.time.Clock;

/**
 * Spring Security 7 (spec 2.1): bez {@code formLogin}, {@code httpBasic}, {@code logout} i {@code HttpSession}
 * (STATELESS, nema {@code JSESSIONID}); sesije su naši tokeni u bazi ({@link GfsAutentikacijaFilter}). Umesto CSRF
 * tokena je provera porekla ({@link ProveraPoreklaFilter}). Header writer-i su isključeni: bezbednosna zaglavlja
 * postavlja host nginx, a Springov {@code Cache-Control} bi pregazio dugi keš {@code /public/mediji}.
 * <p>
 * Pravila su iz {@link PristupMatrica}; poslednje je {@code anyRequest().hasRole("ADMIN")}: zaboravljen endpoint je
 * zaključan. {@code StrictHttpFirewall} (podrazumevani u {@code FilterChainProxy}) odbija {@code ;}, {@code %2f},
 * {@code //}, {@code ..}, {@code \} pre bilo kog pravila; {@link #odbijenZahtev()} od toga pravi 400 JSON.
 * Filteri nisu beanovi (Boot bi ih inače registrovao još jednom kao servlet filtere).
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    static final String POTREBNA_PRIJAVA = "Potrebna je prijava.";
    static final String NEMAS_PRISTUP = "Nemaš pristup.";
    static final String NEISPRAVAN_ZAHTEV = "Neispravan zahtev.";
    static final String MCP_ENDPOINT = "spring.ai.mcp.server.streamable-http.mcp-endpoint";

    /**
     * MCP (spec 3 i 13.2): sopstveni lanac pre glavnog za tačno {@code /mcp}, {@code /mcp/prilozi} i {@code /mcp/slike},
     * {@code permitAll}, bez nastavničke sesije ({@link GfsAutentikacijaFilter} ovde ne radi, pa ni 401 "Potrebna je
     * prijava."), bez provere porekla (Claude Code nije pregledač), bez CORS-a i CSRF-a, stateless. Jedini autoritet je
     * {@code mcp.McpKljucFilter}: servlet filter reda {@code HIGHEST_PRECEDENCE + 10}, pre Spring Security filtera (red
     * {@code -100}), koji bez ključeva odgovara 404, a bez ispravnog {@code Authorization: Bearer} 401 sa
     * {@code WWW-Authenticate: Bearer}. Putanje ispod {@code /mcp} van ove tri idu glavnim lancem (ADMIN).
     * <p>
     * Putanja MCP endpointa je svojstvo Spring AI-ja; matrica je zna kao {@code /mcp}, pa druga vrednost ruši start.
     */
    @Bean
    @Order(1)
    SecurityFilterChain mcpLanac(HttpSecurity http,
                                 @Value("${" + MCP_ENDPOINT + ":/mcp}") String mcpEndpoint) throws Exception {
        if (!"/mcp".equals(mcpEndpoint)) {
            throw new IllegalStateException(MCP_ENDPOINT + " mora biti /mcp (PristupMatrica i MCP lanac znaju tu putanju), "
                    + "a podešeno je " + mcpEndpoint);
        }
        http.securityMatcher(PristupMatrica.matcher(PristupMatrica.mcp()))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain glavniLanac(HttpSecurity http, NastavnikSesijaServis nastavnici, StudentskaPrijava studenti,
                                    Clock clock,
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                    @Value("${gfs.front.url}") String frontUrl)
            throws Exception {
        http.cors(c -> c.configurationSource(cors))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(ulazBezPrijave())
                        .accessDeniedHandler(zabranjeno()))
                .addFilterAfter(new ProveraPoreklaFilter(CorsConfig.origini(frontUrl),
                        ProveraPoreklaFilter.LTI_IZUZECI), CorsFilter.class)
                .addFilterBefore(new GfsAutentikacijaFilter(nastavnici, studenti, clock), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        // greška iz već puštenog zahteva (npr. Tomcat sendError) ne sme postati 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // MCP putanje hvata MCP lanac; ovde ne bi smele stići
                        .requestMatchers(PristupMatrica.matcher(PristupMatrica.mcp())).denyAll()
                        .requestMatchers(PristupMatrica.matcher(PristupMatrica.anonimno())).permitAll()
                        .requestMatchers(PristupMatrica.matcher(PristupMatrica.student())).hasRole("STUDENT")
                        .anyRequest().hasRole("ADMIN"));
        return http.build();
    }

    /** Firewall ({@code StrictHttpFirewall}) odbio zahtev: 400 JSON umesto 500. */
    @Bean
    RequestRejectedHandler odbijenZahtev() {
        return (req, res, e) -> {
            log.info("Zahtev odbijen (firewall), ip={}", KlijentIp.iz(req));
            JsonGreske.posalji(res, 400, NEISPRAVAN_ZAHTEV);
        };
    }

    private static AuthenticationEntryPoint ulazBezPrijave() {
        return (req, res, e) -> JsonGreske.posalji(res, 401, POTREBNA_PRIJAVA);
    }

    private static AccessDeniedHandler zabranjeno() {
        return (req, res, e) -> JsonGreske.posalji(res, 403, NEMAS_PRISTUP);
    }
}
