package com.multiplayer.terracotta.logic;

import org.junit.jupiter.api.Test;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.*;

class ResourceValidatorTest {

    @Test
    void testResourcesExist() {
        String[] requiredResources = {
            "assets/minecraftterracotta/terracotta-0.4.1-windows-x86_64.exe",
            "assets/minecraftterracotta/VCRUNTIME140.DLL"
        };

        for (String path : requiredResources) {
            URL url = getClass().getClassLoader().getResource(path);
            assertNotNull(url, "Resource should exist: " + path);
        }
    }
}
