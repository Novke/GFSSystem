package tri.novica.gfssystem.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//NEMA POTREBE JER ANGULAR SALJE ISTI DEFAULT KAO STO SPRING OCEKUJE
//@Configuration
public class JacksonConfigs {

    @Bean
    public JsonMapperBuilderCustomizer jacksonBuilder() {
        return builder -> {
            builder.defaultDateFormat(new SimpleDateFormat("dd.MM.yyyy"));

            // Jackson 3 ima java.time podrsku ugradjenu; modul sluzi samo za sopstvene formate
            SimpleModule javaTimeModule = new SimpleModule();

            //LOCALDATE
//            javaTimeModule.addSerializer(LocalDate.class,
//                    new LocalDateSerializer(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
//            javaTimeModule.addDeserializer(LocalDate.class,
//                    new LocalDateDeserializer(DateTimeFormatter.ofPattern("dd.MM.yyyy")));


            //LOCALDATETIME
//            javaTimeModule.addSerializer(LocalDateTime.class,
//                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
//            javaTimeModule.addDeserializer(LocalDateTime.class,
//                    new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

            builder.addModule(javaTimeModule);


            builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
