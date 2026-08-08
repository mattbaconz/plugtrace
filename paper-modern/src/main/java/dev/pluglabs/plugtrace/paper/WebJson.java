package dev.pluglabs.plugtrace.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/** Gson for local web API (Gson is provided by the server classpath). */
final class WebJson {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private WebJson() {
    }

    static Gson gson() {
        return GSON;
    }

    static String toJson(Object value) {
        return GSON.toJson(value);
    }

    static byte[] toJsonBytes(Object value) {
        return toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
