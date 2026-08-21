package net.myserver.engine;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.entity.EntityDespawnEvent;
import net.minestom.server.instance.Instance;
import net.myserver.engine.primitive.Long2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Высокопроизводительная пространственная сетка (Spatial Hash Grid).
 * Позволяет выполнять запросы ближайших сущностей за O(1) с нулевыми боксинг-аллокациями.
 */
public class SpatialGrid {
    // Индекс сетки: Instance -> Long2ObjectOpenHashMap<Set<Entity>>
    private static final Map<Instance, Long2ObjectOpenHashMap<Set<Entity>>> grid = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        handler.addListener(EntitySpawnEvent.class, event -> {
            Entity entity = event.getEntity();
            Instance instance = entity.getInstance();
            if (instance != null) {
                addEntity(instance, entity, entity.getPosition());
            }
        });

        handler.addListener(EntityDespawnEvent.class, event -> {
            Entity entity = event.getEntity();
            Instance instance = entity.getInstance();
            if (instance != null) {
                removeEntity(instance, entity, entity.getPosition());
            }
        });
    }

    public static long packChunkCoord(int chunkX, int chunkZ) {
        return FastMath.packChunkPos(chunkX, chunkZ);
    }

    public static void addEntity(Instance instance, Entity entity, Point pos) {
        if (instance == null || entity == null || pos == null) return;
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        long key = FastMath.packChunkPos(cx, cz);

        Long2ObjectOpenHashMap<Set<Entity>> instanceGrid = grid.computeIfAbsent(instance, inst -> new Long2ObjectOpenHashMap<>(64));
        synchronized (instanceGrid) {
            Set<Entity> set = instanceGrid.get(key);
            if (set == null) {
                set = Collections.newSetFromMap(new ConcurrentHashMap<>());
                instanceGrid.put(key, set);
            }
            set.add(entity);
        }
    }

    public static void removeEntity(Instance instance, Entity entity, Point pos) {
        if (instance == null || entity == null || pos == null) return;
        int cx = pos.chunkX();
        int cz = pos.chunkZ();
        long key = FastMath.packChunkPos(cx, cz);

        Long2ObjectOpenHashMap<Set<Entity>> instanceGrid = grid.get(instance);
        if (instanceGrid != null) {
            synchronized (instanceGrid) {
                Set<Entity> set = instanceGrid.get(key);
                if (set != null) {
                    set.remove(entity);
                    if (set.isEmpty()) {
                        instanceGrid.remove(key);
                    }
                }
            }
        }
    }

    public static void updateEntityPosition(Instance instance, Entity entity, Point oldPos, Point newPos) {
        if (instance == null || entity == null) return;
        if (oldPos != null && oldPos.chunkX() == newPos.chunkX() && oldPos.chunkZ() == newPos.chunkZ()) {
            return;
        }
        if (oldPos != null) {
            removeEntity(instance, entity, oldPos);
        }
        addEntity(instance, entity, newPos);
    }

    /**
     * Быстрый поиск всех сущностей в радиусе (в блоках).
     */
    public static List<Entity> getEntitiesInRadius(Instance instance, Point center, double radius) {
        if (instance == null || center == null) return Collections.emptyList();

        List<Entity> result = new ArrayList<>();
        double radiusSq = radius * radius;

        int minChunkX = (int) Math.floor((center.x() - radius) / 16.0);
        int maxChunkX = (int) Math.floor((center.x() + radius) / 16.0);
        int minChunkZ = (int) Math.floor((center.z() - radius) / 16.0);
        int maxChunkZ = (int) Math.floor((center.z() + radius) / 16.0);

        Long2ObjectOpenHashMap<Set<Entity>> instanceGrid = grid.get(instance);
        if (instanceGrid == null) return Collections.emptyList();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Set<Entity> entities;
                synchronized (instanceGrid) {
                    entities = instanceGrid.get(FastMath.packChunkPos(cx, cz));
                }
                if (entities != null && !entities.isEmpty()) {
                    for (Entity e : entities) {
                        if (e.getInstance() == instance && e.getPosition().distanceSquared(center) <= radiusSq) {
                            result.add(e);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Быстрый подсчет сущностей в радиусе (проверка Mob Cap).
     */
    public static int countEntitiesInRadius(Instance instance, Point center, double radius, Predicate<Entity> filter) {
        if (instance == null || center == null) return 0;

        int count = 0;
        double radiusSq = radius * radius;

        int minChunkX = (int) Math.floor((center.x() - radius) / 16.0);
        int maxChunkX = (int) Math.floor((center.x() + radius) / 16.0);
        int minChunkZ = (int) Math.floor((center.z() - radius) / 16.0);
        int maxChunkZ = (int) Math.floor((center.z() + radius) / 16.0);

        Long2ObjectOpenHashMap<Set<Entity>> instanceGrid = grid.get(instance);
        if (instanceGrid == null) return 0;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Set<Entity> entities;
                synchronized (instanceGrid) {
                    entities = instanceGrid.get(FastMath.packChunkPos(cx, cz));
                }
                if (entities != null && !entities.isEmpty()) {
                    for (Entity e : entities) {
                        if (e.getInstance() == instance && (filter == null || filter.test(e))) {
                            if (e.getPosition().distanceSquared(center) <= radiusSq) {
                                count++;
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> T getNearestEntity(Instance instance, Point center, Class<T> entityClass, double maxRadius) {
        List<Entity> inRadius = getEntitiesInRadius(instance, center, maxRadius);
        T nearest = null;
        double minDistanceSq = maxRadius * maxRadius;

        for (Entity e : inRadius) {
            if (entityClass.isInstance(e)) {
                double distSq = e.getPosition().distanceSquared(center);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    nearest = (T) e;
                }
            }
        }

        return nearest;
    }

    public static void clearInstance(Instance instance) {
        grid.remove(instance);
    }
}
