package com.example.sdk.client;

/**
 * Example usage of the EverythingTestClient library demonstrating
 * how to use the MCP Java SDK-based client to interact with the
 * Everything Server.
 * 
 * This example shows the complete flow:
 * 1. Create client with builder pattern
 * 2. Initialize connection
 * 3. Use tools and resources helpers
 * 4. Clean up resources
 */
public class EverythingClientExample {
    
    public static void main(String[] args) {
        System.out.println("🚀 EverythingTestClient Library Example");
        System.out.println("═══════════════════════════════════════");
        
        // Create client with builder pattern
        EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl("http://localhost:4001")  // Change this to your server URL
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();
        
        try {
            // Initialize the connection
            System.out.println("\n📡 Initializing connection to Everything Server...");
            boolean initialized = client.initialize();
            
            if (!initialized) {
                System.err.println("❌ Failed to initialize client. Make sure the Everything Server is running.");
                return;
            }
            
            // Print server information
            client.printServerInfo();
            
            // Test connectivity
            System.out.println("🔗 Testing connectivity...");
            if (client.ping()) {
                System.out.println("✅ Ping successful!");
            } else {
                System.out.println("❌ Ping failed!");
            }
            
            // Use tools helper
            System.out.println("\n🔧 Testing Tools:");
            EverythingTools tools = client.getTools();
            
            // Mathematical operations
            int sum = tools.add(25, 17);
            System.out.println("📊 25 + 17 = " + sum);
            
            // Text operations
            String message = "Hello from EverythingTestClient!";
            String echo = tools.echo(message);
            System.out.println("🔄 Echo: " + echo);
            
            // System operations
            System.out.println("🖥️  Environment variables (first 200 chars):");
            String env = tools.printEnv();
            String truncatedEnv = env.length() > 200 ? env.substring(0, 200) + "..." : env;
            System.out.println("   " + truncatedEnv.replace("\n", "\n   "));
            
            // Available tools
            System.out.println("📋 Available tools: " + tools.getAvailableToolNames());
            
            // Use resources helper
            System.out.println("\n📂 Testing Resources:");
            EverythingResources resources = client.getResources();
            
            int resourceCount = resources.getAvailableResources().size();
            System.out.println("📊 Total resources available: " + resourceCount);
            
            if (resourceCount > 0) {
                // Read a sample resource
                var firstResource = resources.getAvailableResources().get(0);
                System.out.println("📄 Sample resource: " + firstResource.name() + " (" + firstResource.uri() + ")");
                
                try {
                    var resourceContent = resources.readResource(firstResource);
                    System.out.println("✅ Successfully read resource content (" + 
                            resourceContent.contents().size() + " content items)");
                } catch (Exception e) {
                    System.out.println("⚠️  Could not read resource content: " + e.getMessage());
                }
            }
            
            // Long running operation
            System.out.println("\n⏳ Testing long running operation...");
            String result = tools.longRunningOperation(2, 3);
            System.out.println("✅ Long running operation completed: " + result);
            
            System.out.println("\n🎉 All operations completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Error during client operations: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            System.out.println("\n🧹 Cleaning up resources...");
            client.close();
            System.out.println("✅ Client closed successfully");
        }
        
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("📋 Example Summary:");
        System.out.println("   • Builder Pattern: ✅ Used");
        System.out.println("   • Initialization: ✅ Tested");
        System.out.println("   • Server Info: ✅ Displayed");
        System.out.println("   • Tools Helper: ✅ Used");
        System.out.println("   • Resources Helper: ✅ Used");
        System.out.println("   • Resource Cleanup: ✅ Performed");
        System.out.println("🎯 EverythingTestClient library is ready for use!");
    }
}
