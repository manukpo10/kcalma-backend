package com.kcalma.food.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls the Gemini API's {@code generateContent} endpoint with the plate photo inline and a
 * JSON response schema (structured output), so the model's answer is already shaped like
 * {@link FoodAnalysisResult}. See https://ai.google.dev/api/generate-content and
 * https://ai.google.dev/gemini-api/docs/structured-output (current as of 2026-09).
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1beta/models/{model}:generateContent}, auth via the
 * {@code x-goog-api-key} header. Request parts use snake_case ({@code inline_data}/{@code
 * mime_type}) as shown in Google's own REST examples; {@code generationConfig} fields
 * ({@code responseMimeType}/{@code responseSchema}) use camelCase — this mixed casing is
 * correct, not a typo.
 */
@Component
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiFoodPhotoAnalyzer implements FoodPhotoAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GeminiFoodPhotoAnalyzer.class);

    private static final String GENERIC_ERROR = "No se pudo analizar la foto. Probá de nuevo.";

    private static final String PROMPT =
            """
            Sos un nutricionista experto. Mirá la imagen de un plato de comida (con frecuencia \
            cocina argentina) e identificá cada alimento distinto que puedas reconocer.

            Para cada alimento, estimá:
            - los gramos aproximados de esa porción en el plato,
            - sus valores nutricionales por cada 100 gramos, según tablas de composición de \
            alimentos estándar (kcal, proteínas, grasas, carbohidratos, fibra, azúcares y sodio \
            en miligramos).

            Usá nombres de alimentos en español (por ejemplo: "milanesa de carne", "puré de \
            papas", "ensalada mixta"). Si la imagen no muestra comida, o no podés reconocer \
            ningún alimento con confianza razonable, devolvé la lista de alimentos vacía y \
            explicá brevemente el motivo en la nota. Respondé solo con los datos pedidos.""";

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiFoodPhotoAnalyzer(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));

        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public FoodAnalysisResult analyze(byte[] imageBytes, String mimeType) {
        ObjectNode requestBody = buildRequestBody(imageBytes, mimeType);
        JsonNode response;
        try {
            response = restClient
                    .post()
                    .uri("/v1beta/models/{model}:generateContent", properties.getModel())
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini request failed with status {}", e.getStatusCode());
            if (e.getStatusCode().value() == 429) {
                throw new FoodAnalysisException(
                        "Se alcanzó el límite de uso gratuito de Gemini. Probá de nuevo en unos minutos.", e);
            }
            throw new FoodAnalysisException(
                    "El servicio de análisis de fotos no está disponible en este momento.", e);
        } catch (ResourceAccessException e) {
            log.warn("Gemini request timed out or could not connect", e);
            throw new FoodAnalysisException(
                    "El servicio de análisis de fotos tardó demasiado en responder. Probá de nuevo.", e);
        } catch (RestClientException e) {
            log.warn("Unexpected Gemini client error", e);
            throw new FoodAnalysisException(GENERIC_ERROR, e);
        }

        return parseResponse(response);
    }

    ObjectNode buildRequestBody(byte[] imageBytes, String mimeType) {
        ObjectNode textPart = objectMapper.createObjectNode().put("text", PROMPT);

        ObjectNode inlineData = objectMapper.createObjectNode();
        inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
        inlineData.put("data", Base64.getEncoder().encodeToString(imageBytes));
        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.set("inline_data", inlineData);

        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(textPart);
        parts.add(imagePart);
        ObjectNode content = objectMapper.createObjectNode();
        content.set("parts", parts);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("contents", objectMapper.createArrayNode().add(content));

        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", buildResponseSchema());
        root.set("generationConfig", generationConfig);

        return root;
    }

    ObjectNode buildResponseSchema() {
        String[] numberFields = {
            "grams", "kcalPer100", "proteinPer100", "fatPer100", "carbsPer100", "fiberPer100", "sugarPer100",
            "sodiumMgPer100"
        };

        ObjectNode itemProperties = objectMapper.createObjectNode();
        itemProperties.set("name", typeNode("STRING"));
        for (String field : numberFields) {
            itemProperties.set(field, typeNode("NUMBER"));
        }

        ArrayNode itemRequired = objectMapper.createArrayNode().add("name");
        for (String field : numberFields) {
            itemRequired.add(field);
        }

        ObjectNode itemSchema = objectMapper.createObjectNode();
        itemSchema.put("type", "OBJECT");
        itemSchema.set("properties", itemProperties);
        itemSchema.set("required", itemRequired);

        ObjectNode itemsArray = objectMapper.createObjectNode();
        itemsArray.put("type", "ARRAY");
        itemsArray.set("items", itemSchema);

        ObjectNode rootProperties = objectMapper.createObjectNode();
        rootProperties.set("items", itemsArray);
        rootProperties.set("note", typeNode("STRING"));

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");
        schema.set("properties", rootProperties);
        schema.set("required", objectMapper.createArrayNode().add("items"));
        return schema;
    }

    private ObjectNode typeNode(String type) {
        return objectMapper.createObjectNode().put("type", type);
    }

    FoodAnalysisResult parseResponse(JsonNode response) {
        JsonNode textNode =
                response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!textNode.isTextual()) {
            log.warn("Gemini response had no candidate text: {}", response);
            throw new FoodAnalysisException(GENERIC_ERROR);
        }

        JsonNode parsed;
        try {
            // Jackson 3: JacksonException is unchecked, but malformed model output is still an
            // expected failure mode here, so it's still caught and translated explicitly.
            parsed = objectMapper.readTree(textNode.asText());
        } catch (JacksonException e) {
            log.warn("Gemini response text was not valid JSON", e);
            throw new FoodAnalysisException(GENERIC_ERROR, e);
        }

        List<AnalyzedFoodItem> items = new ArrayList<>();
        for (JsonNode item : parsed.path("items")) {
            items.add(new AnalyzedFoodItem(
                    item.path("name").asText(""),
                    item.path("grams").asDouble(0),
                    item.path("kcalPer100").asDouble(0),
                    item.path("proteinPer100").asDouble(0),
                    item.path("fatPer100").asDouble(0),
                    item.path("carbsPer100").asDouble(0),
                    item.path("fiberPer100").asDouble(0),
                    item.path("sugarPer100").asDouble(0),
                    item.path("sodiumMgPer100").asDouble(0)));
        }
        String note = parsed.path("note").isTextual() ? parsed.path("note").asText() : null;
        return new FoodAnalysisResult(items, note);
    }
}
