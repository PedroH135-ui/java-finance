package br.com.javafinance.common;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
@Configuration
public class JsonConfig {
    @Bean Jackson2ObjectMapperBuilderCustomizer decimalStrings() {
        return builder -> {
            builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
            builder.postConfigurer(mapper -> {
                mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            });
        };
    }
}

