package app;

import api.ParkingApi;
import api.WebServer;
import service.ParkingLot;

import java.nio.file.Files;
import java.nio.file.Path;

public class WebMain {
    public static void main(String[] args) throws Exception {
        Path webRoot = Path.of("source_code", "SmartParking", "web");
        if (!Files.isDirectory(webRoot)) {
            System.out.println("Web folder not found: " + webRoot.toAbsolutePath());
            System.out.println("Create it at source_code/SmartParking/web and run again.");
            return;
        }

        ParkingLot lot = DemoData.create();

        WebServer server = new WebServer(8080, webRoot);
        new ParkingApi(lot).register(server);
        server.start();

        System.out.println("SmartPark is running. Open http://localhost:8080 in your browser.");
        System.out.println("Click the red stop square in VS Code to stop the server.");
    }
}