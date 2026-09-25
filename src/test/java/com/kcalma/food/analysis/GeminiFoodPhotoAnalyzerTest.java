package com.kcalma.food.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unit tests for {@link GeminiFoodPhotoAnalyzer}'s request building and response parsing.
 * No real HTTP call is ever made: {@code analyze()} itself is not exercised here, only the pure
 * {@code buildRequestBody}/{@code buildResponseSchema}/{@code parseResponse} steps, against
 * fixtures built with a plain {@link ObjectMapper} (mirroring what Gemini actually returns).
 */
class GeminiFoodPhotoAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiFoodPhotoAnalyzer analyzer = newAnalyzer();

    @Test
    void parseResponse_extractsItemsFromCandidateText() {
        JsonNode response = geminiEnvelope(
                """
                {"items":[
                  {"name":"milanesa de carne","grams":150,"kcalPer100":250,"proteinPer100":22,\
                "fatPer100":15,"carbsPer100":8,"fiberPer100":1,"sugarPer100":0.5,"sodiumMgPer100":450},
                  {"name":"puré de papas","grams":120,"kcalPer100":90,"proteinPer100":2,\
                "fatPer100":2,"carbsPer100":17,"fiberPer100":1.5,"sugarPer100":1,"sodiumMgPer100":200}
                ],"note":null}""");

        FoodAnalysisResult result = analyzer.parseResponse(response);

        assertThat(result.note()).isNull();
        assertThat(result.items()).hasSize(2);
        AnalyzedFoodItem first = result.items().get(0);
        assertThat(first.name()).isEqualTo("milanesa de carne");
        assertThat(first.grams()).isEqualTo(150);
        assertThat(first.kcalPer100()).isEqualTo(250);
        assertThat(first.proteinPer100()).isEqualTo(22);
        assertThat(first.sodiumMgPer100()).isEqualTo(450);
    }

    @Test
    void parseResponse_notFoodImage_returnsEmptyItemsWithNote() {
        JsonNode response = geminiEnvelope("""
                {"items":[],"note":"La imagen no muestra comida."}""");

        FoodAnalysisResult result = analyzer.parseResponse(response);

        assertThat(result.items()).isEmpty();
        assertThat(result.note()).isEqualTo("La imagen no muestra comida.");
    }

    @Test
    void parseResponse_noCandidates_throwsFriendlyException() {
        JsonNode response = mapper.createObjectNode().set("candidates", mapper.createArrayNode());

        assertThatThrownBy(() -> analyzer.parseResponse(response))
                .isInstanceOf(FoodAnalysisException.class)
                .hasMessageContaining("No se pudo analizar");
    }

    @Test
    void parseResponse_candidateTextIsNotJson_throwsFriendlyException() {
        JsonNode response = geminiEnvelope("esto no es json");

        assertThatThrownBy(() -> analyzer.parseResponse(response))
                .isInstanceOf(FoodAnalysisException.class)
                .hasMessageContaining("No se pudo analizar");
    }

    @Test
    void buildRequestBody_usesSnakeCaseInlineDataAndCamelCaseGenerationConfig() {
        byte[] imageBytes = {1, 2, 3, 4};

        ObjectNode body = analyzer.buildRequestBody(imageBytes, "image/jpeg");

        JsonNode parts = body.path("contents").path(0).path("parts");
        assertThat(parts.path(0).path("text").asText()).contains("nutricionista");
        JsonNode inlineData = parts.path(1).path("inline_data");
        assertThat(inlineData.path("mime_type").asText()).isEqualTo("image/jpeg");
        assertThat(inlineData.path("data").asText()).isEqualTo(Base64.getEncoder().encodeToString(imageBytes));

        JsonNode generationConfig = body.path("generationConfig");
        assertThat(generationConfig.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(generationConfig.path("responseSchema").path("type").asText()).isEqualTo("OBJECT");
    }

    @Test
    void buildResponseSchema_declaresItemsArrayAndNullableNote() {
        ObjectNode schema = analyzer.buildResponseSchema();

        assertThat(schema.path("type").asText()).isEqualTo("OBJECT");
        JsonNode itemSchema = schema.path("properties").path("items").path("items");
        assertThat(itemSchema.path("type").asText()).isEqualTo("OBJECT");
        assertThat(itemSchema.path("properties").path("kcalPer100").path("type").asText()).isEqualTo("NUMBER");
        ArrayNode required = (ArrayNode) itemSchema.path("required");
        assertThat(required).extracting(JsonNode::asText).contains("name", "kcalPer100", "sodiumMgPer100");
    }

    private JsonNode geminiEnvelope(String candidateText) {
        ObjectNode part = mapper.createObjectNode().put("text", candidateText);
        ObjectNode content = mapper.createObjectNode();
        content.set("parts", mapper.createArrayNode().add(part));
        ObjectNode candidate = mapper.createObjectNode();
        candidate.set("content", content);
        ObjectNode root = mapper.createObjectNode();
        root.set("candidates", mapper.createArrayNode().add(candidate));
        return root;
    }

    private static GeminiFoodPhotoAnalyzer newAnalyzer() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.5-flash-lite");
        properties.setBaseUrl("https://example.invalid");
        return new GeminiFoodPhotoAnalyzer(properties, RestClient.builder());
    }
}
