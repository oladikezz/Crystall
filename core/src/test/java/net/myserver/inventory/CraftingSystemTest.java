package net.myserver.inventory;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CraftingSystemTest {

    @Test
    public void testCraftPlanks() {
        Material[] grid = new Material[4];
        grid[0] = Material.OAK_LOG;
        grid[1] = Material.AIR;
        grid[2] = Material.AIR;
        grid[3] = Material.AIR;

        ItemStack result = CraftingSystem.checkRecipes(grid, true);
        assertEquals(Material.OAK_PLANKS, result.material());
        assertEquals(4, result.amount());
    }

    @Test
    public void testCraftSticks2x2() {
        Material[] grid = new Material[4];
        grid[0] = Material.OAK_PLANKS;
        grid[1] = Material.AIR;
        grid[2] = Material.OAK_PLANKS;
        grid[3] = Material.AIR;

        ItemStack result = CraftingSystem.checkRecipes(grid, true);
        assertEquals(Material.STICK, result.material());
        assertEquals(4, result.amount());
    }

    @Test
    public void testInvalidCraft() {
        Material[] grid = new Material[4];
        grid[0] = Material.OAK_PLANKS;
        grid[1] = Material.DIRT;
        grid[2] = Material.AIR;
        grid[3] = Material.AIR;

        ItemStack result = CraftingSystem.checkRecipes(grid, true);
        assertEquals(Material.AIR, result.material());
    }
}
