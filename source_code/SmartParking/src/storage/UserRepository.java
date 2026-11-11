package storage;

import service.UserDirectory;

import java.io.IOException;

public interface UserRepository {
    boolean hasSavedUsers();

    void load(UserDirectory directory) throws IOException;

    void save(UserDirectory directory) throws IOException;
}