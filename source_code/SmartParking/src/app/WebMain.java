package app;

import api.ParkingApi;
import api.SettingsApi;
import api.WebServer;
import service.ParkingLot;
import storage.FileParkingRepository;
import storage.ParkingRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class WebMain {
    public static void main(String[] args) throws Exception {
        Path webRoot = Path.of("source_code", "SmartParking", "web");
        if (!Files.isDirectory(webRoot)) {
            System.out.println("Web folder not found: " + webRoot.toAbsolutePath());
            System.out.println("Create it at source_code/SmartParking/web and run again.");
            return;
        }

        Path dataFile = Path.of("data", "smartpark.txt");
        ParkingRepository repository = new FileParkingRepository(dataFile);
        ParkingLot lot = newLot();

        if (repository.hasSavedData()) {
            try {
                repository.load(lot);
                repository.save(lot);
            } catch (IOException e) {
                System.out.println("Could not read " + dataFile + ": " + e.getMessage());
                Path backup = dataFile.resolveSibling("smartpark.broken.txt");
                Files.move(dataFile, backup, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("The old file was kept as " + backup + ". Starting with sample data.");
                lot = newLot();
                DemoData.addSampleData(lot);
                repository.save(lot);
            }
        } else {
            DemoData.addSampleData(lot);
            repository.save(lot);
            System.out.println("No saved data found. Started with sample data and saved it to " + dataFile + ".");
        }

        WebServer server = new WebServer(8080, webRoot);
        new ParkingApi(lot, repository).register(server);
        new SettingsApi(lot, repository).register(server);
        server.start();

        System.out.println("SmartPark is running. Open http://localhost:8080 in your browser.");
        System.out.println("Click the red stop square in VS Code to stop the server.");
    }

    // a lot with the default settings; saved settings replace them when a data file is loaded
    private static ParkingLot newLot() {
        ParkingLot lot = DemoData.createEmptyLot();
        lot.setDailyCapHours(8);
        return lot;
    }
}