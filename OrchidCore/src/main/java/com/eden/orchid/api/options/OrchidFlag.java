package com.eden.orchid.api.options;

import com.eden.common.util.EdenUtils;
import com.eden.orchid.api.options.annotations.Archetype;
import com.eden.orchid.api.options.annotations.Description;
import com.eden.orchid.api.options.annotations.FlagAliases;
import com.eden.orchid.api.options.annotations.Option;
import com.eden.orchid.api.options.annotations.Protected;
import com.eden.orchid.api.options.archetypes.EnvironmentVariableArchetype;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Denotes a Javadoc-style command-line argument. It is important to note that Options are found by scanning the
 * classpath and are **not** created by Dependency Injection, and so are not able to have dependencies injected into
 * themselves.
 *
 * @since v1.0.0
 * @orchidApi extensible
 */
@Archetype(value = EnvironmentVariableArchetype.class, key = "")
public abstract class OrchidFlag {

    public Map<String, FlagDescription> describeFlags() {
        Map<String, FlagDescription> flagDescriptions = new HashMap<>();

        for(Field field : this.getClass().getFields()) {
            if(field.isAnnotationPresent(Option.class)) {
                String flagKey = (!EdenUtils.isEmpty(field.getAnnotation(Option.class).value()))
                        ? field.getAnnotation(Option.class).value()
                        : field.getName();
                String[] aliases = (field.isAnnotationPresent(FlagAliases.class))
                        ? field.getAnnotation(FlagAliases.class).value()
                        : null;
                String description = (field.isAnnotationPresent(Description.class))
                        ? field.getAnnotation(Description.class).value()
                        : null;

                try {
                    FlagDescription des = new FlagDescription(
                            field.getType().getSimpleName(),
                            flagKey,
                            aliases,
                            description
                    );

                    flagDescriptions.put(des.key, des);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return flagDescriptions;
    }

    public Map<String, Value> getParsedFlags() {
        Map<String, Value> flagValues = new HashMap<>();

        for(Field field : this.getClass().getFields()) {
            if(field.isAnnotationPresent(Option.class)) {
                String flagKey = (!EdenUtils.isEmpty(field.getAnnotation(Option.class).value()))
                        ? field.getAnnotation(Option.class).value()
                        : field.getName();
                try {
                    Value value = new Value(
                            field.getType(),
                            flagKey,
                            field.get(this),
                            field.isAnnotationPresent(Protected.class)
                    );

                    flagValues.put(value.key, value);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return flagValues;
    }

    final void loadFlagNames(BiConsumer<String, String[]> consumer) {
        for(Field field : this.getClass().getFields()) {
            if(field.isAnnotationPresent(Option.class)) {
                String flagKey = (!EdenUtils.isEmpty(field.getAnnotation(Option.class).value()))
                        ? field.getAnnotation(Option.class).value()
                        : field.getName();

                String[] aliases = (field.isAnnotationPresent(FlagAliases.class))
                        ? field.getAnnotation(FlagAliases.class).value()
                        : null;

                consumer.accept(flagKey, aliases);
            }
        }
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static final class FlagDescription {

        public final String type;
        public final String key;
        public final String[] aliases;
        public final String description;

    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static final class Value {

        private final Class<?> type;
        private final String key;
        private final Object value;

        private final boolean isProtected;

        public Class<?> getType() {
            if(type.equals(byte.class))   { return Byte.class; }
            if(type.equals(short.class))  { return Short.class; }
            if(type.equals(int.class))    { return Integer.class; }
            if(type.equals(long.class))   { return Long.class; }
            if(type.equals(float.class))  { return Float.class; }
            if(type.equals(double.class)) { return Double.class; }

            return type;
        }
    }

}
