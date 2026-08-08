package dev.pluglabs.plugtrace.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Gson helpers for SQLite JSON columns (server provides Gson at runtime). */
final class JsonCodec {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    String toJson(Object value) {
        return gson.toJson(value);
    }

    <T> List<T> list(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Type type = TypeToken.getParameterized(List.class, elementType).getType();
        List<T> parsed = gson.fromJson(json, type);
        return parsed == null ? List.of() : Collections.unmodifiableList(parsed);
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
