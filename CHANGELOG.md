# 📋 Changelog — Crystall Core

## [1.0.0] - 2026-08-21 (Official Production Release)

### 🚀 Major Architectural Breakthroughs
- **SpatialGrid Engine:** $O(1)$ spatial hash grid replacing global $O(N)$ entity scanning for radius and nearest entity lookups.
- **AdaptiveTickEngine (LOD Zones):** 4-tier Level of Detail ticking (20 Hz, 4 Hz, 1 Hz, 0 Hz) saving 60–80% idle CPU power.
- **FastMath Lookup Table (LUT):** 16,384-point precomputed trigonometric LUT with `FastMath.sin()`, `FastMath.cos()`, `FastMath.invSqrt()`, and 64-bit coordinate packing.
- **Zero-Box Primitive Structures:** Custom `Long2ObjectOpenHashMap` and `LongOpenHashSet` to eliminate boxed `java.lang.Long` and `Map.Entry` allocations.
- **32×32 Region Chunk Storage:** Palette-based chunk encoding with section skipping (empty air sections bypassed) and Thread-Local zero-GC Deflater/Inflater buffers.
- **Alternate-Current BFS Redstone:** Breadth-First Search signal propagation eliminating deep call stacks and string property churn.
- **Dynamic View Distance & Performance Monitor:** Real-time nano-precision MSPT rolling window tracker and dynamic view distance adjustment.

### 🎮 Pure Core Vanilla Mechanics
- Block breaking hardness and tool durability calculations (`BlockMechanics`).
- Sand/gravel falling block gravity (`PhysicsSystem`).
- Fluid flow simulation with zero-GC circular buffer queues (`FluidSystem`).
- Crop and plant growth simulation (`RandomTickSystem`).
- 2×2 player inventory and 3×3 crafting tables with vanilla recipes (`CraftingSystem`).
- Combat formula with armor reduction, weapon cooldowns, knockback, and respawn (`CombatSystem`).
- Natural monster spawning with local mob caps and surface verification (`MobSpawnerSystem`).
- Overworld <-> Nether portal teleportation with 1:8 coordinate translation (`DimensionManager`).
- Day/Night cycle, rain, thunder, bed sleep skipping, fishing, item frames, and player head drops.

### 🛡️ Security, Monitoring & Administration
- Anticheat movement checks (Fly/Speedhack).
- Anti-bot and packet flood rate-limiting.
- Anti-dupe inventory tracking.
- REST API endpoints on port `:25566` (`/api/status`, `/api/performance`, `/api/benchmark`, `/api/stresstest`, `/metrics`).
- Live WebMap server on port `:8080`.
- In-engine micro-benchmark suite (`/benchmark run`).
