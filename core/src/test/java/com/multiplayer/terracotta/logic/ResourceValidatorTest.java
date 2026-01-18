package com.multiplayer.terracotta.logic;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ResourceValidatorTest {

    @Test
    void testRequiredResourcesConfiguredCorrectly() throws Exception {
        Field field = ResourceValidator.class.getDeclaredField("REQUIRED_RESOURCES");
        field.setAccessible(true);
        String[] requiredResources = (String[]) field.get(null);

        String[] expected = {
                "assets/minecraftterracotta/terracotta-0.4.1-windows-x86_64.exe",
                "assets/minecraftterracotta/VCRUNTIME140.DLL"
        };

        assertArrayEquals(expected, requiredResources);
    }
}
