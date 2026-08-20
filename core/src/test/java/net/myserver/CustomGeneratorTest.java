package net.myserver;

import net.myserver.world.CustomGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomGeneratorTest {

    @Test
    public void testGeneratorCreation() {
        CustomGenerator generator = new CustomGenerator(12345);
        assertNotNull(generator);
    }
}
