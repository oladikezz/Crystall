package me.neznamy.tab.api;

import java.util.UUID;

public interface TabPlayer {

   UUID getUniqueId();

   String getName();

   Object getPlayer();
}
