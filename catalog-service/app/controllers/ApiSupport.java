package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Shared request validation and response helpers for Catalog endpoints.
 * Validation failures are returned as 400 responses by the calling controller.
 */
final class ApiSupport {
    private ApiSupport() {
    }

    static ObjectNode error(String code, String message) {
        ObjectNode response = Json.newObject();
        response.put("code", code);
        response.put("message", message);
        return response;
    }

    static String requiredText(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required and must be non-empty text");
        }
        return value.asText().trim();
    }

    static String optionalText(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text or null");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    static long requiredPositiveLong(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.asLong();
    }

    static boolean optionalBoolean(JsonNode body, String field, boolean defaultValue) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.asBoolean();
    }

    static LocalDate optionalDate(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must use YYYY-MM-DD format");
        }
    }

    static int optionalNonNegativeInt(JsonNode body, String field, int defaultValue) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        return value.asInt();
    }

    static JsonNode jsonBody(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        return body;
    }

    static int page(String value) {
        return positiveQueryInt(value, 1, "page");
    }

    static int size(String value) {
        int size = positiveQueryInt(value, 20, "size");
        return Math.min(size, 100);
    }

    private static int positiveQueryInt(String value, int defaultValue, String field) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
    }
}
