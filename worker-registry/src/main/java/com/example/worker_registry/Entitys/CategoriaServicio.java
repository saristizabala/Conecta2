package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Locale;

public enum CategoriaServicio {
    PLOMERIA,
    CARPINTERIA,
    ASEO,
    ELECTRICIDAD,
    PINTURA,
    JARDINERIA,
    COSTURA,
    COCINA,
    TECNOLOGIA
    // Si tienes OTROS, agrégalo aquí: , OTROS
    ;

    @JsonCreator
    public static CategoriaServicio fromJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        String n = normalize(value);

        switch (n) {
            case "plomeria":     return PLOMERIA;
            case "carpinteria":  return CARPINTERIA;
            case "aseo":         return ASEO;
            case "electricidad": return ELECTRICIDAD;
            case "pintura":      return PINTURA;
            case "jardineria":   return JARDINERIA;
            case "costura":      return COSTURA;
            case "cocina":       return COCINA;
            case "tecnologia":   return TECNOLOGIA;
            // case "otros":        return OTROS; // <-- solo si lo tienes en el enum
            default:
                throw new IllegalArgumentException("Categoría inválida: " + value);
        }
    }

    @JsonValue
    public String toJson() {
        // Devolvemos el nombre exacto del enum (como string JSON)
        return this.name();
    }

    private static String normalize(String s) {
        String noAccent = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccent.toLowerCase(Locale.ROOT).trim();
    }
}
