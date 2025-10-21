package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Arrays;

public enum CategoriaServicio {
    PLOMERIA,
    CARPINTERIA,
    ASEO,
    ELECTRICIDAD,
    PINTURA,
    JARDINERIA,
    COSTURA,
    COCINA,
    TECNOLOGIA;

    @JsonCreator
    public static CategoriaServicio fromJson(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('-', ' ')
                .trim()
                .toUpperCase()
                .replace(' ', '_');

        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Categoria no valida: " + raw));
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
