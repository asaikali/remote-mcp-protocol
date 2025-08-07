package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test client for validating MCP Everything Server functionality.
 * This client provides a high-level API for testing MCP servers and proxies
 * using RestClient for HTTP communication.
 */
public class EverythingServerTestClient {
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private String sessionId;
    private boolean initialized = false;
    
    public EverythingServerTestClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Initialize the MCP connection and store the session ID
     */
    public InitializationResult initialize() {
        String initRequestJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {
                      "roots": { "listChanged": true },
                      "sampling": {},
                      "elicitation": {}
                    },
                    "clientInfo": {
                      "name": "EverythingServerTestClient",
                      "version": "1.0.0"
                    }
                  }
                }
                """;

        try {
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(initRequestJson)
                    .retrieve()
                    .toEntity(String.class);

            assertNotNull(response, "Initialize response should not be null");
            assertNotNull(response.getBody(), "Response body should not be null");

            // Extract session ID
            this.sessionId = response.getHeaders().getFirst("mcp-session-id");
            assertNotNull(sessionId, "Session ID should be present in headers");
            assertFalse(sessionId.trim().isEmpty(), "Session ID should not be empty");

            // Parse response
            String jsonContent = extractJsonFromResponse(response.getBody());
            JsonNode responseJson = objectMapper.readTree(jsonContent);
            
            // Validate response structure
            assertEquals("2.0", responseJson.get("jsonrpc").asText());
            assertEquals(1, responseJson.get("id").asInt());
            assertTrue(responseJson.has("result"));
            
            JsonNode result = responseJson.get("result");
            String protocolVersion = result.get("protocolVersion").asText();
            JsonNode serverInfo = result.get("serverInfo");
            
            // Send initialized notification
            sendInitializedNotification();
            
            this.initialized = true;
            
            return new InitializationResult(sessionId, protocolVersion, serverInfo);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MCP connection", e);
        }
    }
    
    /**
     * Send the initialized notification after successful initialization
     */
    private void sendInitializedNotification() {
        if (sessionId == null) {
            throw new IllegalStateException("Must initialize before sending notification");
        }
        
        String notificationJson = """
                [
                  {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized"
                  }
                ]
                """;

        try {
            restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .body(notificationJson)
                    .retrieve()
                    .toEntity(String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send initialized notification", e);
        }
    }
    
    /**
     * List all available tools from the server
     */
    public ToolsListResult listTools() {
        ensureInitialized();
        
        String listToolsJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/list"
                }
                """;

        try {
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .body(listToolsJson)
                    .retrieve()
                    .toEntity(String.class);

            String jsonContent = extractJsonFromResponse(response.getBody());
            JsonNode responseJson = objectMapper.readTree(jsonContent);
            
            assertEquals("2.0", responseJson.get("jsonrpc").asText());
            assertEquals(2, responseJson.get("id").asInt());
            assertTrue(responseJson.has("result"));
            
            JsonNode result = responseJson.get("result");
            assertTrue(result.has("tools"));
            assertTrue(result.get("tools").isArray());
            
            List<String> toolNames = new ArrayList<>();
            for (JsonNode tool : result.get("tools")) {
                toolNames.add(tool.get("name").asText());
            }
            
            return new ToolsListResult(toolNames);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to list tools", e);
        }
    }
    
    /**
     * Invoke the echo tool with a message and return the result
     */
    public EchoResult invokeEcho(String message) {
        ensureInitialized();
        
        String echoRequestJson = String.format("""
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "echo",
                    "arguments": {
                      "message": "%s"
                    }
                  }
                }
                """, message);

        try {
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .body(echoRequestJson)
                    .retrieve()
                    .toEntity(String.class);

            String jsonContent = extractJsonFromResponse(response.getBody());
            JsonNode responseJson = objectMapper.readTree(jsonContent);
            
            assertEquals("2.0", responseJson.get("jsonrpc").asText());
            assertEquals(3, responseJson.get("id").asInt());
            assertTrue(responseJson.has("result"));
            
            JsonNode result = responseJson.get("result");
            assertTrue(result.has("content"));
            
            JsonNode content = result.get("content").get(0);
            String text = content.get("text").asText();
            
            return new EchoResult(text);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke echo tool", e);
        }
    }
    
    /**
     * Invoke the add tool with two numbers and return the result
     */
    public AddResult invokeAdd(int a, int b) {
        ensureInitialized();
        
        String addRequestJson = String.format("""
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "method": "tools/call",
                  "params": {
                    "name": "add",
                    "arguments": {
                      "a": %d,
                      "b": %d
                    }
                  }
                }
                """, a, b);

        try {
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .body(addRequestJson)
                    .retrieve()
                    .toEntity(String.class);

            String jsonContent = extractJsonFromResponse(response.getBody());
            JsonNode responseJson = objectMapper.readTree(jsonContent);
            
            assertEquals("2.0", responseJson.get("jsonrpc").asText());
            assertEquals(4, responseJson.get("id").asInt());
            assertTrue(responseJson.has("result"));
            
            JsonNode result = responseJson.get("result");
            assertTrue(result.has("content"));
            
            JsonNode content = result.get("content").get(0);
            String text = content.get("text").asText();
            
            // Parse the result from the text (e.g. "2 + 3 = 5")
            int resultValue = parseAddResult(text, a, b);
            
            return new AddResult(resultValue, text);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke add tool", e);
        }
    }
    
    /**
     * Validate that the server has the expected core tools
     */
    public void validateCoreTools() {
        ToolsListResult tools = listTools();
        Set<String> expectedTools = Set.of("echo", "printEnv", "add", "longRunningOperation");
        
        for (String expectedTool : expectedTools) {
            assertTrue(tools.toolNames().contains(expectedTool), 
                "Expected tool '" + expectedTool + "' not found. Available tools: " + tools.toolNames());
        }
    }
    
    /**
     * Run a comprehensive test suite against the everything server
     */
    public TestSuiteResult runTestSuite() {
        try {
            // Initialize
            InitializationResult init = initialize();
            
            // List and validate tools
            ToolsListResult tools = listTools();
            validateCoreTools();
            
            // Test echo
            EchoResult echo = invokeEcho("Hello, MCP World!");
            assertTrue(echo.message().contains("Hello, MCP World!"), 
                "Echo result should contain the input message");
            
            // Test add
            AddResult add = invokeAdd(2, 3);
            assertEquals(5, add.result(), "2 + 3 should equal 5");
            
            return new TestSuiteResult(
                true,
                init.sessionId(),
                tools.toolNames().size(),
                echo.message(),
                add.result()
            );
            
        } catch (Exception e) {
            return new TestSuiteResult(false, null, 0, null, 0);
        }
    }
    
    // Helper methods
    
    private void ensureInitialized() {
        if (!initialized || sessionId == null) {
            throw new IllegalStateException("Client must be initialized first");
        }
    }
    
    private String extractJsonFromResponse(String responseBody) {
        if (responseBody.contains("event:") && responseBody.contains("data:")) {
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    return line.substring(6);
                }
            }
        }
        return responseBody;
    }
    
    private int parseAddResult(String text, int a, int b) {
        // Handle various possible formats:
        // "2 + 3 = 5" or "The sum of 2 and 3 is 5." or similar
        
        // Try format: "The sum of X and Y is Z"
        String pattern1 = "The sum of " + a + " and " + b + " is ";
        if (text.contains(pattern1)) {
            String resultPart = text.substring(text.indexOf(pattern1) + pattern1.length()).trim();
            // Extract the number (remove punctuation)
            String numberStr = resultPart.replaceAll("[^0-9].*", "");
            return Integer.parseInt(numberStr);
        }
        
        // Try format: "X + Y = Z"
        String pattern2 = a + " + " + b + " = ";
        if (text.contains(pattern2)) {
            String resultPart = text.substring(text.indexOf(pattern2) + pattern2.length()).trim();
            return Integer.parseInt(resultPart.split("\\s+")[0]);
        }
        
        // Try extracting any number that equals a + b
        int expected = a + b;
        if (text.contains(String.valueOf(expected))) {
            return expected;
        }
        
        throw new RuntimeException("Could not parse add result from: " + text);
    }
    
    // Result record classes
    
    public record InitializationResult(String sessionId, String protocolVersion, JsonNode serverInfo) {}
    
    public record ToolsListResult(List<String> toolNames) {}
    
    public record EchoResult(String message) {}
    
    public record AddResult(int result, String fullText) {}
    
    public record TestSuiteResult(
        boolean success,
        String sessionId,
        int toolCount,
        String echoMessage,
        int addResult
    ) {}
}