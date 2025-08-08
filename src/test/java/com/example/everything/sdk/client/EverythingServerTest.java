package com.example.everything.sdk.client;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that provides MCP Everything Server containers for both transport types.
 * 
 * This class sets up two separate Everything Server containers:
 * - One running in HTTP_STREAMABLE mode (streamableHttp)
 * - One running in HTTP_SSE mode (sse)
 * 
 * This allows any test class that extends this to write arbitrary tests against either
 * transport protocol without worrying about server setup.
 * 
 * Usage:
 * ```java
 * class MyCustomTest extends EverythingServerTest {
 *     @Test
 *     void testSomething() {
 *         String streamableUrl = getStreamableServerUrl();
 *         String sseUrl = getSseServerUrl();
 *         // Test whatever you want...
 *     }
 * }
 * ```
 * 
 * Features:
 * - Two independent MCP servers (streamable + SSE)
 * - Connectivity validation
 * - Helper methods for getting server URLs
 * - Spring Boot test context integration
 * - Testcontainers lifecycle management
 */
@SpringBootTest
@Testcontainers
@DisplayName("Everything Server Test Infrastructure")
public abstract class EverythingServerTest {

    protected static final int MCP_PORT = 3001;

    // HTTP_STREAMABLE server container
    @Container
    static GenericContainer<?> streamableContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "streamableHttp")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*MCP Streamable HTTP Server listening on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    // HTTP_SSE server container  
    @Container
    static GenericContainer<?> sseContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "sse")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is running on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Register both server configurations for Spring Boot
        registry.add("mcp.server.streamable.host", streamableContainer::getHost);
        registry.add("mcp.server.streamable.port", streamableContainer::getFirstMappedPort);
        registry.add("mcp.server.sse.host", sseContainer::getHost);
        registry.add("mcp.server.sse.port", sseContainer::getFirstMappedPort);
    }

    /**
     * Get the complete URL for the HTTP_STREAMABLE server.
     * @return URL in format "http://host:port"
     */
    protected String getStreamableServerUrl() {
        return String.format("http://%s:%d", 
                streamableContainer.getHost(), 
                streamableContainer.getFirstMappedPort());
    }

    /**
     * Get the complete URL for the HTTP_SSE server.
     * @return URL in format "http://host:port"
     */
    protected String getSseServerUrl() {
        return String.format("http://%s:%d", 
                sseContainer.getHost(), 
                sseContainer.getFirstMappedPort());
    }

    /**
     * Get the HTTP_STREAMABLE server container instance.
     * Useful for advanced container operations.
     * @return the streamable server container
     */
    protected GenericContainer<?> getStreamableContainer() {
        return streamableContainer;
    }

    /**
     * Get the HTTP_SSE server container instance.
     * Useful for advanced container operations.
     * @return the SSE server container
     */
    protected GenericContainer<?> getSseContainer() {
        return sseContainer;
    }

    /**
     * Get the mapped port for the HTTP_STREAMABLE server.
     * @return the mapped port number
     */
    protected int getStreamableServerPort() {
        return streamableContainer.getFirstMappedPort();
    }

    /**
     * Get the mapped port for the HTTP_SSE server.
     * @return the mapped port number
     */
    protected int getSseServerPort() {
        return sseContainer.getFirstMappedPort();
    }

    /**
     * Get the host for the HTTP_STREAMABLE server.
     * @return the container host
     */
    protected String getStreamableServerHost() {
        return streamableContainer.getHost();
    }

    /**
     * Get the host for the HTTP_SSE server.
     * @return the container host
     */
    protected String getSseServerHost() {
        return sseContainer.getHost();
    }

    /**
     * Test basic network connectivity to both MCP servers.
     * This validates that the containers are running and ports are accessible.
     */
    @Test
    @DisplayName("Should have network connectivity to both MCP servers")
    void testServerConnectivity() {
        // Test HTTP_STREAMABLE server connectivity
        String streamableHost = getStreamableServerHost();
        int streamablePort = getStreamableServerPort();
        
        assertTrue(isPortOpen(streamableHost, streamablePort), 
                "HTTP_STREAMABLE server should be accessible at " + streamableHost + ":" + streamablePort);
        
        // Test HTTP_SSE server connectivity
        String sseHost = getSseServerHost();
        int ssePort = getSseServerPort();
        
        assertTrue(isPortOpen(sseHost, ssePort), 
                "HTTP_SSE server should be accessible at " + sseHost + ":" + ssePort);
        
        System.out.println("✅ Network Connectivity Verified:");
        System.out.println("📡 HTTP_STREAMABLE: " + getStreamableServerUrl());
        System.out.println("📡 HTTP_SSE: " + getSseServerUrl());
    }

    /**
     * Helper method to check if a TCP port is open and accepting connections.
     * @param host the hostname or IP address
     * @param port the port number
     * @return true if the port is open, false otherwise
     */
    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
