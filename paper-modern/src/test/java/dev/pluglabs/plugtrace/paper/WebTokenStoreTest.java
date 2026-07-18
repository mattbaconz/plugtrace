package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebTokenStoreTest {
    @TempDir Path temp;

    @Test
    void storesOnlyHashesAndEnforcesScopes() throws Exception {
        WebTokenStore store = new WebTokenStore(temp.resolve("tokens.properties"));
        String read = store.create("reader", WebTokenStore.Scope.READ);
        String admin = store.create("admin", WebTokenStore.Scope.ADMIN);

        assertTrue(store.verify(read, WebTokenStore.Scope.READ));
        assertFalse(store.verify(read, WebTokenStore.Scope.ADMIN));
        assertTrue(store.verify(admin, WebTokenStore.Scope.ADMIN));
        assertFalse(java.nio.file.Files.readString(temp.resolve("tokens.properties")).contains(read));
    }
}
