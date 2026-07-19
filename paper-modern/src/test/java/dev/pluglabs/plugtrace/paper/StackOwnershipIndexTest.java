package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.ComponentIdentity;
import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.ComponentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackOwnershipIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesJarOwnedFramesAndMarksKnownWrappersSeparately() throws Exception {
        Path jar = tempDir.resolve("Shop.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("com/acme/shop/Menu.class"));
            out.write(new byte[] {0});
            out.closeEntry();
        }
        ComponentSnapshot shop = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "1.0", "hash", List.of(),
                        List.of(), List.of(), "com.acme.shop.ShopPlugin", "1.21"),
                "plugins/Shop.jar", Files.size(jar), true, true, null, jar.toString());
        WrapperRegistry wrappers = WrapperRegistry.load(new ByteArrayInputStream(
                "com.comphenix.protocol.=ProtocolLib\n".getBytes(StandardCharsets.UTF_8)));

        StackOwnershipIndex index = StackOwnershipIndex.build(List.of(shop), wrappers);
        List<String> ownership = index.resolve(
                "java.lang.RuntimeException: boom\n"
                        + "\tat com.comphenix.protocol.events.PacketAdapter.onPacket(PacketAdapter.java:1)\n"
                        + "\tat com.acme.shop.Menu.open(Menu.java:9)",
                List.of("ShopLogger"));

        assertEquals("wrapper:ProtocolLib", ownership.get(0));
        assertTrue(ownership.contains("frame:Shop"));
        assertTrue(ownership.contains("logger:ShopLogger"));
    }

    @Test
    void resolvesPaperJarPrefixedFrames() throws Exception {
        Path jar = tempDir.resolve("Shop.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("com/acme/shop/Menu.class"));
            out.write(new byte[] {0});
            out.closeEntry();
        }
        ComponentSnapshot shop = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "1.0", "hash", List.of(),
                        List.of(), List.of(), "com.acme.shop.ShopPlugin", "1.21"),
                "plugins/Shop.jar", Files.size(jar), true, true, null, jar.toString());

        StackOwnershipIndex index = StackOwnershipIndex.build(List.of(shop), WrapperRegistry.packaged());
        List<String> ownership = index.resolve(
                "java.lang.RuntimeException: boom\n"
                        + "\tat Shop.jar//com.acme.shop.Menu.open(Menu.java:9)\n"
                        + "\tat PlugTraceFixture-WrapperChain-0.4.0.jar//"
                        + "com.acme.shop.Menu.open(Menu.java:9)",
                List.of());

        assertTrue(ownership.contains("frame:Shop"));
    }

    @Test
    void packagedWrapperRegistryIsDeclarativeAndReadable() throws Exception {
        try (var input = getClass().getResourceAsStream("/wrapper-registry.properties")) {
            assertTrue(input != null);
            assertTrue(WrapperRegistry.load(input).ownerFor("com.comphenix.protocol.events.PacketEvent")
                    .isPresent());
        }
    }
}
