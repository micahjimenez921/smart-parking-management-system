package storage;

import service.UserDirectory;
import user.Role;
import user.User;
import user.UserFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileUserRepository implements UserRepository {
    private static final String HEADER = "SMARTPARK-USERS 1";

    private final Path file;

    public FileUserRepository(Path file) {
        this.file = file;
    }

    @Override
    public boolean hasSavedUsers() {
        return Files.isRegularFile(file);
    }

    @Override
    public void save(UserDirectory directory) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(TextCodec.join("COUNTER", String.valueOf(directory.getLastIdNumber())));
        for (User u : directory.getUsers()) {
            lines.add(TextCodec.join("USER", u.getId(), u.getRole().name(), u.getUsername(),
                    u.getName(), u.getPasswordHash(), u.getDetail()));
        }

        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);

        Path temp = folder.resolve(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void load(UserDirectory directory) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IOException("This is not a SmartPark user file.");
        }

        int loaded = 0;
        int skipped = 0;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;

            try {
                String[] p = TextCodec.split(line);
                if ("COUNTER".equals(p[0])) {
                    directory.setLastIdNumber(Integer.parseInt(p[1]));
                    continue;
                }
                if (!"USER".equals(p[0])) {
                    throw new IllegalArgumentException("unknown record type " + p[0]);
                }
                String detail = p.length > 6 ? p[6] : "";
                User user = UserFactory.create(Role.parse(p[2]), p[1], p[4], p[3], p[5], detail);
                directory.restore(user);
                loaded++;
            } catch (ArrayIndexOutOfBoundsException e) {
                skipped++;
                System.out.println("Skipped line " + (i + 1) + " of the user file: some fields are missing.");
            } catch (RuntimeException e) {
                skipped++;
                System.out.println("Skipped line " + (i + 1) + " of the user file: " + e.getMessage());
            }
        }

        System.out.println("Loaded " + loaded + " user account(s)"
                + (skipped > 0 ? " (" + skipped + " line(s) skipped)" : "") + ".");
    }
}