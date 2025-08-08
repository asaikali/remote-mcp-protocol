package com.example.http;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Scanner;

public class McpEverythingWebClient {

    private static final String BASE_URL = "http://localhost:4001/mcp";

    public static void main(String[] args) {
        WebClient client = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();

        Scanner scanner = new Scanner(System.in);

        // 1. Initialize
        System.out.println("============================================================");
        System.out.println("  STAGE 1: INITIALIZE");
        System.out.println("============================================================");

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
                      "name": "SpringWebClient",
                      "version": "0.1"
                    }
                  }
                }
                """;

        var initResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(initRequestJson)
                .retrieve()
                .toEntity(String.class)
                .block();

        System.out.println("Initialization Response:");
        System.out.println(initResponse.getBody());

        String sessionId = initResponse.getHeaders().getFirst("mcp-session-id");
        System.out.println("✅ MCP Session ID: " + sessionId);

        System.out.print("➡️ Press Enter to send initialized notification...");
        scanner.nextLine();

        // 2. Initialized notification
        System.out.println("============================================================");
        System.out.println("  STAGE 2: INITIALIZED NOTIFICATION");
        System.out.println("============================================================");

        String initializedNotificationJson = """
                [
                  {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized"
                  }
                ]
                """;

        var notifyResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .bodyValue(initializedNotificationJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Initialized Notification Response:");
        System.out.println(notifyResponse);

        System.out.print("➡️ Press Enter to LIST TOOLS...");
        scanner.nextLine();

        // 3. List tools
        System.out.println("============================================================");
        System.out.println("  STAGE 3: LIST TOOLS");
        System.out.println("============================================================");

        String listToolsRequestJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/list"
                }
                """;

        var toolsResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .bodyValue(listToolsRequestJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Tools Response:");
        System.out.println(toolsResponse);

        System.out.print("➡️ Press Enter to invoke ECHO tool...");
        scanner.nextLine();

        // 4. Invoke echo tool
        System.out.println("============================================================");
        System.out.println("  STAGE 4: INVOKE ECHO TOOL");
        System.out.println("============================================================");

        String echoRequestJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "echo",
                    "arguments": {
                      "message": "Hello from Spring WebClient!"
                    }
                  }
                }
                """;

        var echoResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .bodyValue(echoRequestJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Echo Response:");
        System.out.println(echoResponse);

        System.out.print("➡️ Press Enter to invoke LONG RUNNING OPERATION (without progressToken)...");
        scanner.nextLine();

        // 5. LongRunningOperation without progress token
        System.out.println("============================================================");
        System.out.println("  STAGE 5: LONG RUNNING OPERATION (NO progressToken)");
        System.out.println("============================================================");

        String longOpNoProgressJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "method": "tools/call",
                  "params": {
                    "name": "longRunningOperation",
                    "arguments": {
                      "duration": 10,
                      "steps": 5
                    }
                  }
                }
                """;

        var longOpNoProgressResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .bodyValue(longOpNoProgressJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Long Running Operation Response (no progress):");
        System.out.println(longOpNoProgressResponse);

        System.out.print("➡️ Press Enter to invoke LONG RUNNING OPERATION (with progressToken)...");
        scanner.nextLine();

        // 6. LongRunningOperation with progress token
        System.out.println("============================================================");
        System.out.println("  STAGE 6: LONG RUNNING OPERATION (WITH progressToken)");
        System.out.println("============================================================");

        String longOpWithProgressJson = """
                {
                  "jsonrpc": "2.0",
                  "id": 5,
                  "method": "tools/call",
                  "params": {
                    "name": "longRunningOperation",
                    "arguments": {
                      "duration": 10,
                      "steps": 5
                    },
                    "_meta": {
                      "progressToken": "op-5678"
                    }
                  }
                }
                """;

        var longOpWithProgressResponse = client.post()
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .bodyValue(longOpWithProgressJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Long Running Operation Response (with progressToken):");
        System.out.println(longOpWithProgressResponse);

        System.out.println("✅ DONE. Keep SSE listener running in a separate terminal to see notifications.");
    }
}
