package land.webgui.paper;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** plugin.yml is code too: a typo in it is a plugin that does not load. */
class PluginYmlTest {
    private static PluginDescriptionFile load() throws IOException, InvalidDescriptionException {
        try (InputStream in = PluginYmlTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml is on the classpath");
            return new PluginDescriptionFile(in);
        }
    }

    @Test
    void namesTheRealMainClass() throws Exception {
        PluginDescriptionFile d = load();
        assertEquals("WebGUI", d.getName());
        assertEquals(WebGUIPaperPlugin.class.getName(), d.getMain());
        assertEquals("1.21", d.getAPIVersion());
    }

    @Test
    void versionIsExpandedFromTheBuild() throws Exception {
        String version = load().getVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "got " + version);
    }

    @Test
    void declaresTheCommand() throws Exception {
        assertTrue(load().getCommands().containsKey("webgui"));
    }

    @Test
    void everySubcommandPermissionIsDeclaredForOpsAndGrantedByTheParent() throws Exception {
        List<Permission> permissions = load().getPermissions();
        Permission parent = permissions.stream().filter(p -> p.getName().equals("webgui.command")).findFirst().orElseThrow();
        assertEquals(PermissionDefault.OP, parent.getDefault());
        for (String sub : List.of("gui", "hud", "bind", "unbind", "reload")) {
            String name = "webgui.command." + sub;
            Permission child = permissions.stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
            assertEquals(PermissionDefault.OP, child.getDefault(), name);
            assertEquals(Boolean.TRUE, parent.getChildren().get(name), name);
        }
    }
}
