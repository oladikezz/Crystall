package ru.lor.watcher.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.meta = this.itemStack.getItemMeta();
    }

    public ItemBuilder name(Component component) {
        if (meta != null) {
            meta.displayName(component);
        }
        return this;
    }

    public ItemBuilder name(String miniMessageText) {
        return name(ColorUtil.parse(miniMessageText));
    }

    public ItemBuilder lore(Component... lines) {
        if (meta != null) {
            meta.lore(Arrays.asList(lines));
        }
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        if (meta != null) {
            meta.lore(lines);
        }
        return this;
    }

    public ItemBuilder loreStrings(String... lines) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(ColorUtil.parse(line));
        }
        return lore(components);
    }

    public ItemBuilder loreStrings(List<String> lines) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(ColorUtil.parse(line));
        }
        return lore(components);
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
