package app;

import api.AccessGuard;
import api.AuthApi;
import api.DriverApi;
import api.ParkingApi;
import api.SettingsApi;
import api.UserApi;
import api.WebServer;
import security.AccessPolicy;
import security.PasswordHasher;
import security.SessionManager;
import service.AuthService;
import service.ParkingLot;
import service.UserDirectory;
import storage.FileParkingRepository;
import storage.FileUserRepository;
import storage.ParkingRepository;
import storage.UserRepository;
import user.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

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

        Path userFile = Path.of("data", "users.txt");
        UserRepository userRepository = new FileUserRepository(userFile);
        UserDirectory directory = new UserDirectory();
        boolean firstRun = !userRepository.hasSavedUsers();

        if (!firstRun) {
            try {
                userRepository.load(directory);
            } catch (IOException e) {
                System.out.println("Could not read " + userFile + ": " + e.getMessage());
                Path backup = userFile.resolveSibling("users.broken.txt");
                Files.move(userFile, backup, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("The old file was kept as " + backup + ". Starting with new accounts.");
                directory = new UserDirectory();
                firstRun = true;
            }
        }
        createStartingAccounts(directory, firstRun);
        userRepository.save(directory);

        SessionManager sessions = new SessionManager(Duration.ofMinutes(30));
        AuthService auth = new AuthService(directory, sessions);
        AccessGuard guard = new AccessGuard(auth, AccessPolicy.standard());

        WebServer server = new WebServer(8080, webRoot);
        server.setGuard(guard);
        new AuthApi(auth, directory, userRepository, guard).register(server);
        new UserApi(auth, directory, userRepository, guard).register(server);
        new DriverApi(lot, repository, directory, userRepository, guard).register(server);
        new ParkingApi(lot, repository).register(server);
        new SettingsApi(lot, repository).register(server);
        server.start();

        System.out.println("SmartPark is running. Open http://localhost:8080 in your browser.");
        System.out.println("Click the red stop square in VS Code to stop the server.");
    }

    // Makes sure someone can sign in. The generated passwords are shown here once and are never saved as text.
    private static void createStartingAccounts(UserDirectory directory, boolean firstRun) {
        String administrator = directory.ensureAdministrator();
        if (administrator != null) {
            System.out.println("Administrator account created (username / password): " + administrator);
        }
        if (firstRun) {
            addSampleAccount(directory, Role.ATTENDANT, "Sample Attendant", "attendant", "DAY");
            addSampleAccount(directory, Role.DRIVER, "Sample Driver", "driver", "SAM 123");
            System.out.println("Write these passwords down now. They are not shown again.");
            System.out.println("Change them on the My account page, or delete data/users.txt to start over.");
        }
    }

    private static void addSampleAccount(UserDirectory directory, Role role, String name,
                                         String username, String detail) {
        String password = PasswordHasher.generate(12);
        directory.create(role, name, username, password, detail);
        System.out.println("Sample " + role.name().toLowerCase() + " account (username / password): "
                + username + " / " + password);
    }

    // a lot with the default settings; saved settings replace them when a data file is loaded
    private static ParkingLot newLot() {
        ParkingLot lot = DemoData.createEmptyLot();
        lot.setDailyCapHours(8);
        return lot;
    }
}