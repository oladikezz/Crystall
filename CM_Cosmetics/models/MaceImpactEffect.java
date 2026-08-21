package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public enum MaceImpactEffect {
    DUST_RING {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 8; i++) {
                final double radius = 0.35 + (i * 0.2);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 20, Particle.CLOUD, 1, 0.02);
                    spawnBlockRing(frame, radius, 14, Material.DIRT);
                });
            }
        }
    },
    GROUND_CRACK {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            schedule(center, 0, () -> {
                spawnBurst(center, Particle.FLASH, 1, 0.0);
                spawnBurst(center, Particle.SMOKE, 12, 0.18);
                spawnBurst(center, Particle.CLOUD, 14, 0.2);
                spawnBlockBurst(center, Material.STONE, 20, 0.35);
            });
            schedule(center, 2, () -> spawnBlockBurst(center, Material.DEEPSLATE, 16, 0.4));
        }
    },
    IMPACT_FLASH {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            schedule(center, 0, () -> {
                spawnBurst(center, Particle.FLASH, 1, 0.0);
                spawnBurst(center, Particle.FLASH, 1, 0.0);
                spawnBurst(center, Particle.CLOUD, 18, 0.28);
            });
        }
    },
    SHOCKWAVE_RING {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 12; i++) {
                final double radius = 0.4 + (i * 0.22);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 18, Particle.CLOUD, 1, 0.015);
                    spawnRing(frame, radius * 0.92, 14, Particle.SMOKE, 1, 0.01);
                });
            }
        }
    },
    ELECTRO_SPARK {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 6; i++) {
                final Location frame = loc.clone();
                final int step = i;
                final double radius = 0.3 + (i * 0.16);
                schedule(frame, i, () -> {
                    spawnBurst(frame, Particle.ELECTRIC_SPARK, 14, 0.28);
                    spawnRing(frame, radius, 10, Particle.ELECTRIC_SPARK, 1, 0.01);
                    if (step == 0 || step == 3) {
                        spawnBurst(frame, Particle.FLASH, 1, 0.0);
                    }
                });
            }
        }
    },
    FLAME_BURST {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 8; i++) {
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnBurst(frame, Particle.FLAME, 16, 0.35);
                    spawnBurst(frame, Particle.SMOKE, 10, 0.25);
                });
            }
        }
    },
    FROST_CRACK {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 10; i++) {
                final double radius = 0.35 + (i * 0.18);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnBurst(frame, Particle.SNOWFLAKE, 14, 0.35);
                    spawnRedstoneRing(frame, radius, 14, Color.fromRGB(220, 240, 255), 1.0f);
                });
            }
        }
    },
    SHADOW_IMPACT {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 12; i++) {
                final double radius = 0.4 + (i * 0.17);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnBurst(frame, Particle.SOUL, 10, 0.3);
                    spawnBurst(frame, Particle.PORTAL, 12, 0.35);
                    spawnRing(frame, radius, 12, Particle.SMOKE, 1, 0.01);
                });
            }
        }
    },
    VORTEX_SPIRAL {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 18; i++) {
                final int step = i;
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    for (int p = 0; p < 6; p++) {
                        double angle = step * 0.45 + (p * (Math.PI / 3));
                        double radius = 0.25 + (step * 0.03);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        double y = 0.05 + (step * 0.06);
                        Location point = frame.clone().add(x, y, z);
                        spawnBurst(point, Particle.PORTAL, 1, 0.0);
                        spawnBurst(point, Particle.WITCH, 1, 0.0);
                    }
                });
            }
        }
    },
    SWEEP_ARC {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Vector baseDir = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (baseDir.lengthSquared() < 1.0E-4) {
                baseDir = attacker.getLocation().getDirection();
            }
            baseDir.setY(0).normalize();
            Vector right = baseDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

            for (int i = 0; i <= 4; i++) {
                final double radius = 0.8 + (i * 0.12);
                final Location frame = loc.clone().add(0, 0.35, 0);
                final Vector dir = baseDir.clone();
                final Vector side = right.clone();
                schedule(frame, i, () -> {
                    for (int p = -8; p <= 8; p++) {
                        double t = p / 8.0;
                        double angle = t * (Math.PI / 2);
                        Vector point = dir.clone().multiply(Math.cos(angle) * radius)
                            .add(side.clone().multiply(Math.sin(angle) * radius));
                        Location particleLoc = frame.clone().add(point);
                        spawnBurst(particleLoc, Particle.SWEEP_ATTACK, 1, 0.0);
                        spawnBurst(particleLoc, Particle.CLOUD, 1, 0.0);
                    }
                });
            }
        }
    },
    THUNDER_CAGE {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 10; i++) {
                final int step = i;
                final double radius = 0.8 + (i * 0.08);
                final Location frame = loc.clone().add(0, 0.2, 0);
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 20, Particle.ELECTRIC_SPARK, 1, 0.01);
                    spawnRing(frame.clone().add(0, 0.45, 0), radius * 0.9, 14, Particle.END_ROD, 1, 0.0);
                    if (step % 3 == 0) {
                        spawnBurst(frame, Particle.FLASH, 1, 0.0);
                    }
                });
            }
        }
    },
    VOLCANIC_ERUPTION {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 12; i++) {
                final int step = i;
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    double y = 0.1 + (step * 0.09);
                    Location up = frame.clone().add(0, y, 0);
                    spawnBurst(up, Particle.FLAME, 18, 0.35);
                    spawnBurst(up, Particle.LAVA, 8, 0.25);
                    spawnBurst(up, Particle.SMOKE, 10, 0.3);
                    if (step == 0 || step == 6) {
                        spawnBurst(frame, Particle.FLASH, 1, 0.0);
                    }
                });
            }
        }
    },
    GLACIER_PRISON {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 10; i++) {
                final double radius = 0.7 + (i * 0.11);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 18, Particle.SNOWFLAKE, 1, 0.0);
                    spawnRedstoneRing(frame.clone().add(0, 0.12, 0), radius * 0.85, 14, Color.fromRGB(170, 225, 255), 1.2f);
                    spawnBlockRing(frame, radius * 0.7, 10, Material.PACKED_ICE);
                });
            }
        }
    },
    ABYSSAL_RUPTURE {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 14; i++) {
                final double radius = 0.5 + (i * 0.1);
                final Location frame = loc.clone();
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 14, Particle.REVERSE_PORTAL, 1, 0.01);
                    spawnBurst(frame, Particle.SOUL_FIRE_FLAME, 10, 0.28);
                    spawnBurst(frame, Particle.SMOKE, 10, 0.22);
                });
            }
        }
    },
    STARFALL_BURST {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            for (int i = 0; i <= 8; i++) {
                final int step = i;
                schedule(center, i, () -> {
                    double y = 1.8 - (step * 0.18);
                    Location p = center.clone().add(0, Math.max(0.1, y), 0);
                    spawnBurst(p, Particle.END_ROD, 14, 0.26);
                    spawnBurst(p, Particle.FIREWORK, 8, 0.2);
                    if (step == 8) {
                        spawnBurst(center, Particle.FLASH, 1, 0.0);
                        spawnBurst(center, Particle.FLASH, 1, 0.0);
                        spawnBurst(center, Particle.CLOUD, 24, 0.35);
                    }
                });
            }
        }
    },
    SONIC_CRATER {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            schedule(center, 0, () -> {
                spawnBurst(center, Particle.SONIC_BOOM, 1, 0.0);
                spawnBurst(center, Particle.FLASH, 1, 0.0);
                spawnBurst(center, Particle.CLOUD, 20, 0.32);
                spawnBlockBurst(center, Material.DEEPSLATE, 24, 0.42);
            });
            for (int i = 1; i <= 8; i++) {
                final double radius = 0.7 + (i * 0.18);
                final Location frame = center.clone();
                schedule(frame, i, () -> {
                    spawnRing(frame, radius, 16, Particle.CLOUD, 1, 0.01);
                    spawnRing(frame, radius * 0.9, 14, Particle.SMOKE, 1, 0.01);
                });
            }
        }
    },
    PRISM_LANCE {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            for (int i = 0; i <= 10; i++) {
                final int step = i;
                schedule(center, i, () -> {
                    double height = 0.15 + (step * 0.16);
                    for (int p = 0; p < 3; p++) {
                        double angle = (Math.PI * 2 * p) / 3.0 + (step * 0.1);
                        double r = 0.45;
                        Location point = center.clone().add(Math.cos(angle) * r, height, Math.sin(angle) * r);
                        spawnBurst(point, Particle.END_ROD, 2, 0.0);
                        spawnBurst(point, Particle.GLOW, 1, 0.0);
                    }
                    if (step == 10) {
                        spawnBurst(center.clone().add(0, 1.9, 0), Particle.FLASH, 1, 0.0);
                    }
                });
            }
        }
    },
    PETAL_TORUS {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 12; i++) {
                final int step = i;
                final Location frame = loc.clone().add(0, 0.5, 0);
                schedule(frame, i, () -> {
                    double major = 0.55 + (step * 0.08);
                    double minor = 0.18 + (step * 0.01);
                    for (int a = 0; a < 16; a++) {
                        double theta = (Math.PI * 2 * a) / 16.0;
                        for (int b = 0; b < 8; b++) {
                            double phi = (Math.PI * 2 * b) / 8.0;
                            double x = (major + minor * Math.cos(phi)) * Math.cos(theta);
                            double z = (major + minor * Math.cos(phi)) * Math.sin(theta);
                            double y = minor * Math.sin(phi) * 0.7;
                            Location point = frame.clone().add(x, y, z);
                            spawnBurst(point, Particle.CHERRY_LEAVES, 1, 0.0);
                        }
                    }
                });
            }
        }
    },
    GRAVITY_WELL {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            for (int i = 0; i <= 12; i++) {
                final int step = i;
                final Location frame = loc.clone().add(0, 0.15, 0);
                schedule(frame, i, () -> {
                    double radius = 2.2 - (step * 0.15);
                    for (int p = 0; p < 20; p++) {
                        double angle = (Math.PI * 2 * p) / 20.0;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location outer = frame.clone().add(x, 0, z);
                        Vector inward = frame.toVector().subtract(outer.toVector()).normalize().multiply(0.08);
                        spawnBurst(outer, Particle.REVERSE_PORTAL, 1, 0.0);
                        spawnBurst(outer.clone().add(inward), Particle.ENCHANT, 1, 0.0);
                    }
                    if (step == 12) {
                        spawnBurst(frame, Particle.FLASH, 1, 0.0);
                        spawnBurst(frame, Particle.FLASH, 1, 0.0);
                    }
                });
            }
        }
    },
    RUNE_MATRIX {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone().add(0, 0.06, 0);
            for (int i = 0; i <= 8; i++) {
                final double size = 0.7 + (i * 0.12);
                final Location frame = center.clone();
                schedule(frame, i, () -> {
                    for (double x = -size; x <= size; x += size / 4.0) {
                        spawnBurst(frame.clone().add(x, 0, -size), Particle.ENCHANT, 1, 0.0);
                        spawnBurst(frame.clone().add(x, 0, size), Particle.ENCHANT, 1, 0.0);
                    }
                    for (double z = -size; z <= size; z += size / 4.0) {
                        spawnBurst(frame.clone().add(-size, 0, z), Particle.ENCHANT, 1, 0.0);
                        spawnBurst(frame.clone().add(size, 0, z), Particle.ENCHANT, 1, 0.0);
                    }
                    spawnRing(frame, size * 0.65, 8, Particle.END_ROD, 1, 0.0);
                });
            }
        }
    },
    TIDAL_COLUMN {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            for (int i = 0; i <= 14; i++) {
                final int step = i;
                schedule(center, i, () -> {
                    double y = 0.08 + (step * 0.11);
                    double radius = 0.4 + (step * 0.02);
                    for (int p = 0; p < 10; p++) {
                        double angle = step * 0.35 + (p * (Math.PI * 2 / 10));
                        Location point = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                        spawnBurst(point, Particle.BUBBLE_COLUMN_UP, 1, 0.0);
                        spawnBurst(point, Particle.SPLASH, 1, 0.0);
                    }
                    if (step == 14) {
                        spawnBurst(center.clone().add(0, 1.8, 0), Particle.SPLASH, 20, 0.35);
                    }
                });
            }
        }
    },
    OBSIDIAN_TEAR_RAIN {
        @Override
        public void play(Location loc, Player attacker, LivingEntity target) {
            Location center = loc.clone();
            for (int i = 0; i <= 10; i++) {
                final int step = i;
                schedule(center, i, () -> {
                    double height = 1.8 - (step * 0.14);
                    for (int p = 0; p < 14; p++) {
                        double angle = (Math.PI * 2 * p) / 14.0;
                        double radius = 0.8 + (Math.sin(step * 0.6 + p) * 0.25);
                        Location drop = center.clone().add(Math.cos(angle) * radius, Math.max(0.1, height), Math.sin(angle) * radius);
                        spawnBurst(drop, Particle.DRIPPING_OBSIDIAN_TEAR, 1, 0.0);
                        spawnBurst(drop.clone().add(0, -0.25, 0), Particle.FALLING_OBSIDIAN_TEAR, 1, 0.0);
                    }
                    if (step == 10) {
                        spawnBurst(center, Particle.LANDING_OBSIDIAN_TEAR, 16, 0.28);
                    }
                });
            }
        }
    };

    public abstract void play(Location loc, Player attacker, LivingEntity target);

    public static MaceImpactEffect fromConfig(String value, String cosmeticId) {
        if (value != null && !value.isBlank()) {
            try {
                return MaceImpactEffect.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String id = cosmeticId == null ? "" : cosmeticId.toLowerCase();
        if (id.contains("shockwave")) return SHOCKWAVE_RING;
        if (id.contains("storm") || id.contains("electro")) return ELECTRO_SPARK;
        if (id.contains("void") || id.contains("shadow")) return SHADOW_IMPACT;
        if (id.contains("frost") || id.contains("ice")) return FROST_CRACK;
        if (id.contains("inferno") || id.contains("flame")) return FLAME_BURST;
        if (id.contains("sonic") || id.contains("flash")) return IMPACT_FLASH;
        if (id.contains("vortex")) return VORTEX_SPIRAL;
        if (id.contains("sweep")) return SWEEP_ARC;
        if (id.contains("crack")) return GROUND_CRACK;
        if (id.contains("thunder")) return THUNDER_CAGE;
        if (id.contains("volcanic") || id.contains("eruption")) return VOLCANIC_ERUPTION;
        if (id.contains("glacier")) return GLACIER_PRISON;
        if (id.contains("abyss")) return ABYSSAL_RUPTURE;
        if (id.contains("starfall")) return STARFALL_BURST;
        if (id.contains("crater")) return SONIC_CRATER;
        if (id.contains("prism")) return PRISM_LANCE;
        if (id.contains("petal") || id.contains("torus")) return PETAL_TORUS;
        if (id.contains("gravity")) return GRAVITY_WELL;
        if (id.contains("rune")) return RUNE_MATRIX;
        if (id.contains("tidal") || id.contains("water")) return TIDAL_COLUMN;
        if (id.contains("tear") || id.contains("obsidian")) return OBSIDIAN_TEAR_RAIN;
        return DUST_RING;
    }

    private static void schedule(Location loc, long delayTicks, Runnable action) {
        Location snapshot = loc.clone();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("DoAPI");
        if (plugin == null || !plugin.isEnabled()) {
            action.run();
            return;
        }
        try {
            if (delayTicks <= 0L) {
                Bukkit.getRegionScheduler().execute(plugin, snapshot, action);
            } else {
                Bukkit.getRegionScheduler().runDelayed(plugin, snapshot, task -> action.run(), delayTicks);
            }
        } catch (Throwable ignored) {
            action.run();
        }
    }

    private static void spawnBurst(Location loc, Particle particle, int count, double spread) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            world.spawnParticle(particle, loc, count, spread, spread * 0.5, spread, 0.01);
        } catch (Exception ignored) {
        }
    }

    private static void spawnBlockBurst(Location loc, Material material, int count, double spread) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            world.spawnParticle(Particle.BLOCK, loc, count, spread, 0.08, spread, 0.01, material.createBlockData());
        } catch (Exception ignored) {
        }
    }

    private static void spawnBlockRing(Location loc, double radius, int points, Material material) {
        World world = loc.getWorld();
        if (world == null) return;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = loc.clone().add(x, 0.02, z);
            try {
                world.spawnParticle(Particle.BLOCK, p, 1, 0, 0, 0, 0, material.createBlockData());
            } catch (Exception ignored) {
            }
        }
    }

    private static void spawnRedstoneRing(Location loc, double radius, int points, Color color, float size) {
        World world = loc.getWorld();
        if (world == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = loc.clone().add(x, 0.05, z);
            try {
                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
            } catch (Exception ignored) {
            }
        }
    }

    private static void spawnRing(Location loc, double radius, int points, Particle particle, int count, double speed) {
        World world = loc.getWorld();
        if (world == null) return;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = loc.clone().add(x, 0.05, z);
            try {
                world.spawnParticle(particle, p, count, 0, 0, 0, speed);
            } catch (Exception ignored) {
            }
        }
    }
}

