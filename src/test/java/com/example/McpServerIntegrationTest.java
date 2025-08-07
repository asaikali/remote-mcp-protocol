package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class McpServerIntegrationTest {

    private static final int MCP_PORT = 3001;
    private static final String MCP_ENDPOINT = "/mcp";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static GenericContainer<?> mcpContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "streamableHttp")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*MCP Streamable HTTP Server listening on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mcp.server.host", mcpContainer::getHost);
        registry.add("mcp.server.port", mcpContainer::getFirstMappedPort);
    }

    private RestClient createRestClient() {
        String baseUrl = String.format("http://%s:%d%s", 
                mcpContainer.getHost(), 
                mcpContainer.getFirstMappedPort(), 
                MCP_ENDPOINT);
        
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Test
    void shouldInitializeMcpServer() throws Exception {
        // Given
        RestClient client = createRestClient();
        
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
                      "name": "SpringTestClient",
                      "version": "1.0.0"
                    }
                  }
                }
                """;

        // When
        ResponseEntity<String> initResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(initRequestJson)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertNotNull(initResponse, "Initialize response should not be null");
        assertNotNull(initResponse.getBody(), "Response body should not be null");
        
        // Verify session ID is present
        String sessionId = initResponse.getHeaders().getFirst("mcp-session-id");
        assertNotNull(sessionId, "Session ID should be present in headers");
        assertFalse(sessionId.trim().isEmpty(), "Session ID should not be empty");
        
        // Extract response body
        String responseBody = initResponse.getBody();
        
        // Handle Server-Sent Events format if needed
        String jsonContent = responseBody;
        if (responseBody.contains("event:") && responseBody.contains("data:")) {
            // Extract JSON from SSE format: data: {json}
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    jsonContent = line.substring(6); // Remove "data: " prefix
                    break;
                }
            }
        }
        
        // Verify response structure
        JsonNode responseJson = objectMapper.readTree(jsonContent);
        assertEquals("2.0", responseJson.get("jsonrpc").asText(), "Should be JSON-RPC 2.0");
        assertEquals(1, responseJson.get("id").asInt(), "Response ID should match request ID");
        assertTrue(responseJson.has("result"), "Response should have result field");
        
        JsonNode result = responseJson.get("result");
        assertTrue(result.has("protocolVersion"), "Result should have protocolVersion");
        assertTrue(result.has("capabilities"), "Result should have capabilities");
        assertTrue(result.has("serverInfo"), "Result should have serverInfo");
        
        // Verify server info
        JsonNode serverInfo = result.get("serverInfo");
        assertTrue(serverInfo.has("name"), "Server info should have name");
        assertTrue(serverInfo.has("version"), "Server info should have version");
        
        System.out.println("✅ MCP Server initialized successfully");
        System.out.println("📋 Session ID: " + sessionId);
        System.out.println("📋 Server Info: " + serverInfo.toString());
    }

    @Test
    void shouldSendInitializedNotification() throws Exception {
        // Given
        RestClient client = createRestClient();
        String sessionId = initializeServer(client);
        
        String initializedNotificationJson = """
                [
                  {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized"
                  }
                ]
                """;

        // When
        ResponseEntity<String> notifyResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .body(initializedNotificationJson)
                .retrieve()
                .toEntity(String.class);

        // Then
        // Notification responses are typically empty or minimal
        System.out.println("✅ Initialized notification sent successfully");
        System.out.println("📋 Response: " + notifyResponse.getBody());
    }

    @Test
    void shouldListToolsAfterInitialization() throws Exception {
        // Given
        RestClient client = createRestClient();
        String sessionId = initializeServerAndSendNotification(client);
        
        String listToolsRequestJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/list"
                }
                """;

        // When
        ResponseEntity<String> toolsResponseEntity = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .body(listToolsRequestJson)
                .retrieve()
                .toEntity(String.class);
        
        String toolsResponse = toolsResponseEntity.getBody();

        // Then
        assertNotNull(toolsResponse, "Tools response should not be null");
        
        // Handle potential SSE format
        String jsonContent = toolsResponse;
        if (toolsResponse.contains("event:") && toolsResponse.contains("data:")) {
            String[] lines = toolsResponse.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    jsonContent = line.substring(6);
                    break;
                }
            }
        }
        
        JsonNode responseJson = objectMapper.readTree(jsonContent);
        assertEquals("2.0", responseJson.get("jsonrpc").asText());
        assertEquals(2, responseJson.get("id").asInt());
        assertTrue(responseJson.has("result"), "Response should have result field");
        
        JsonNode result = responseJson.get("result");
        assertTrue(result.has("tools"), "Result should have tools array");
        assertTrue(result.get("tools").isArray(), "Tools should be an array");
        assertTrue(result.get("tools").size() > 0, "Should have at least one tool available");
        
        System.out.println("✅ Tools listed successfully");
        System.out.println("📋 Available tools: " + result.get("tools").size());
    }

    /**
     * Helper method to initialize the MCP server and return the session ID
     */
    private String initializeServer(RestClient client) throws Exception {
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
                      "name": "SpringTestClient",
                      "version": "1.0.0"
                    }
                  }
                }
                """;

        ResponseEntity<String> initResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(initRequestJson)
                .retrieve()
                .toEntity(String.class);

        assertNotNull(initResponse);
        String sessionId = initResponse.getHeaders().getFirst("mcp-session-id");
        assertNotNull(sessionId, "Session ID should be available after initialization");
        
        // Handle potential SSE format in helper method too
        String responseBody = initResponse.getBody();
        if (responseBody != null && responseBody.contains("event:") && responseBody.contains("data:")) {
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    String jsonContent = line.substring(6);
                    JsonNode responseJson = objectMapper.readTree(jsonContent);
                    // Verify it's a valid initialization response
                    if (responseJson.has("result")) {
                        break; // Valid response found
                    }
                }
            }
        }
        
        return sessionId;
    }

    /**
     * Helper method to initialize the server and send initialized notification
     */
    private String initializeServerAndSendNotification(RestClient client) throws Exception {
        String sessionId = initializeServer(client);
        
        String initializedNotificationJson = """
                [
                  {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized"
                  }
                ]
                """;

        client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .body(initializedNotificationJson)
                .retrieve()
                .toEntity(String.class);
                
        return sessionId;
    }
}