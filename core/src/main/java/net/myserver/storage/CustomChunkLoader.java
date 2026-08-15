package net.myserver.storage;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CustomChunkLoader implements IChunkLoader {
    private final File chunkFolder;

    public CustomChunkLoader(String folderPath) {
        this.chunkFolder = new File(folderPath, "chunks");
        if (!this.chunkFolder.exists()) {
            this.chunkFolder.mkdirs();
        }
    }

    @Override
    public CompletableFuture<Chunk> loadChunk(Instance instance, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            File file = new File(chunkFolder, chunkX + "_" + chunkZ + ".dat");
            if (!file.exists()) return null;

            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
                Chunk chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);

                for (int y = -64; y < 320; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            short stateId = in.readShort();
                            if (stateId != 0) {
                                chunk.setBlock(x, y, z, Block.fromStateId(stateId));
                            }
                        }
                    }
                }
                return chunk;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveChunk(Chunk chunk) {
        return CompletableFuture.runAsync(() -> {
            File file = new File(chunkFolder, chunk.getChunkX() + "_" + chunk.getChunkZ() + ".dat");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
                for (int y = -64; y < 320; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            out.writeShort(chunk.getBlock(x, y, z).stateId());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean supportsParallelSaving() { return true; }

    @Override
    public boolean supportsParallelLoading() { return true; }
}
