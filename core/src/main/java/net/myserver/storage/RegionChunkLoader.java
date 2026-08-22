package net.myserver.storage;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.myserver.engine.FastMath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Высокопроизводительный загрузчик чанков в формате Region Files (32x32 чанка на файл).
 * Включает Thread-Local пулы сжатия (Zero-GC Deflate/Inflate), палитровое сжатие и пропуск пустых секций.
 */
public class RegionChunkLoader implements ChunkLoader {
    private static final Logger log = LoggerFactory.getLogger(RegionChunkLoader.class);
    private static final int SECTOR_HEADER_SIZE = 8192; // 1024 * 8 байт (offset: int, size: int)
    private static final int TOTAL_SECTIONS = 24;       // от Y=-64 до Y=320 (384 / 16 = 24 секции)
    private static final int BUFFER_SIZE = 262144;      // 256 КБ буфер для сжатия

    private final File regionFolder;
    private final Map<Long, RegionFile> openRegions = new ConcurrentHashMap<>();

    // Thread-Local компрессоры для исключения повторных аллокаций
    private static final ThreadLocal<Deflater> deflaterPool = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION));
    private static final ThreadLocal<Inflater> inflaterPool = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<byte[]> ioBufferPool = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    public RegionChunkLoader(String basePath) {
        this.regionFolder = new File(basePath, "regions");
        if (!this.regionFolder.exists()) {
            this.regionFolder.mkdirs();
        }
    }

    private static class RegionFile implements AutoCloseable {
        private final RandomAccessFile file;
        private final int[] offsets = new int[1024];
        private final int[] lengths = new int[1024];

        public RegionFile(File path) throws IOException {
            this.file = new RandomAccessFile(path, "rw");
            if (file.length() < SECTOR_HEADER_SIZE) {
                file.setLength(SECTOR_HEADER_SIZE);
                Arrays.fill(offsets, 0);
                Arrays.fill(lengths, 0);
            } else {
                file.seek(0);
                for (int i = 0; i < 1024; i++) {
                    offsets[i] = file.readInt();
                    lengths[i] = file.readInt();
                }
            }
        }

        public synchronized byte[] readChunk(int index) throws IOException {
            int offset = offsets[index];
            int length = lengths[index];
            if (offset < SECTOR_HEADER_SIZE || length <= 0) {
                return null;
            }
            file.seek(offset);
            byte[] data = new byte[length];
            file.readFully(data);
            return data;
        }

        public synchronized void writeChunk(int index, byte[] data, int length) throws IOException {
            int offset = offsets[index];

            if (offset < SECTOR_HEADER_SIZE || length > lengths[index]) {
                offset = (int) file.length();
            }

            file.seek(offset);
            file.write(data, 0, length);

            offsets[index] = offset;
            lengths[index] = length;

            file.seek((long) index * 8);
            file.writeInt(offset);
            file.writeInt(length);
        }

        @Override
        public synchronized void close() throws IOException {
            file.close();
        }
    }

    private RegionFile getRegionFile(int regionX, int regionZ) {
        long key = FastMath.packChunkPos(regionX, regionZ);
        return openRegions.computeIfAbsent(key, k -> {
            try {
                File file = new File(regionFolder, "r." + regionX + "." + regionZ + ".dat");
                return new RegionFile(file);
            } catch (IOException e) {
                log.error("[RegionChunkLoader] Ошибка открытия региона ({}, {}): {}", regionX, regionZ, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public @Nullable Chunk loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        int rx = chunkX >> 5;
        int rz = chunkZ >> 5;
        int lx = chunkX & 31;
        int lz = chunkZ & 31;
        int index = lx + (lz * 32);

        RegionFile rf = getRegionFile(rx, rz);
        if (rf == null) return null;

        try {
            byte[] compressed = rf.readChunk(index);
            if (compressed == null) return null;

            // Распаковка через ThreadLocal Inflater (Zero-GC)
            Inflater inflater = inflaterPool.get();
            inflater.reset();
            inflater.setInput(compressed);

            byte[] decompressed = ioBufferPool.get();
            int decompressedBytes = inflater.inflate(decompressed);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(decompressed, 0, decompressedBytes));
            Chunk chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);
            int sectionMask = in.readInt();

            for (int s = 0; s < TOTAL_SECTIONS; s++) {
                if ((sectionMask & (1 << s)) == 0) {
                    continue;
                }

                int startY = -64 + (s * 16);
                int paletteSize = in.readInt();
                int[] palette = new int[paletteSize];
                for (int p = 0; p < paletteSize; p++) {
                    palette[p] = in.readInt();
                }

                if (paletteSize == 1) {
                    Block b = Block.fromStateId(palette[0]);
                    if (b != null && !b.compare(Block.AIR)) {
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    chunk.setBlock(x, startY + y, z, b);
                                }
                            }
                        }
                    }
                } else {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int paletteIdx = in.readByte() & 0xFF;
                                if (paletteIdx < paletteSize) {
                                    int stateId = palette[paletteIdx];
                                    if (stateId != 0) {
                                        Block b = Block.fromStateId(stateId);
                                        if (b != null) {
                                            chunk.setBlock(x, startY + y, z, b);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return chunk;
        } catch (Exception e) {
            log.error("[RegionChunkLoader] Ошибка десериализации чанка ({}, {}): {}", chunkX, chunkZ, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveChunk(@NotNull Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        int rx = chunkX >> 5;
        int rz = chunkZ >> 5;
        int lx = chunkX & 31;
        int lz = chunkZ & 31;
        int index = lx + (lz * 32);

        RegionFile rf = getRegionFile(rx, rz);
        if (rf == null) return;

        try {
            byte[] rawBuffer = ioBufferPool.get();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(rawBuffer.length);
            DataOutputStream out = new DataOutputStream(baos);

            int sectionMask = 0;

            for (int s = 0; s < TOTAL_SECTIONS; s++) {
                int startY = -64 + (s * 16);
                boolean hasBlocks = false;
                for (int y = 0; y < 16 && !hasBlocks; y++) {
                    for (int x = 0; x < 16 && !hasBlocks; x++) {
                        for (int z = 0; z < 16 && !hasBlocks; z++) {
                            if (!chunk.getBlock(x, startY + y, z).compare(Block.AIR)) {
                                hasBlocks = true;
                            }
                        }
                    }
                }
                if (hasBlocks) {
                    sectionMask |= (1 << s);
                }
            }

            out.writeInt(sectionMask);

            for (int s = 0; s < TOTAL_SECTIONS; s++) {
                if ((sectionMask & (1 << s)) == 0) continue;

                int startY = -64 + (s * 16);
                Map<Integer, Integer> paletteMap = new LinkedHashMap<>();
                List<Integer> paletteList = new ArrayList<>();
                int[] blockStates = new int[4096];

                int idx = 0;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int stateId = chunk.getBlock(x, startY + y, z).stateId();
                            blockStates[idx++] = stateId;
                            if (!paletteMap.containsKey(stateId)) {
                                paletteMap.put(stateId, paletteList.size());
                                paletteList.add(stateId);
                            }
                        }
                    }
                }

                out.writeInt(paletteList.size());
                for (int sid : paletteList) {
                    out.writeInt(sid);
                }

                if (paletteList.size() > 1) {
                    for (int i = 0; i < 4096; i++) {
                        int pIdx = paletteMap.get(blockStates[i]);
                        out.writeByte(pIdx);
                    }
                }
            }

            byte[] uncompressed = baos.toByteArray();

            // Сжатие через ThreadLocal Deflater
            Deflater deflater = deflaterPool.get();
            deflater.reset();
            deflater.setInput(uncompressed);
            deflater.finish();

            byte[] compressedBuffer = new byte[uncompressed.length + 64];
            int compressedSize = deflater.deflate(compressedBuffer);

            rf.writeChunk(index, compressedBuffer, compressedSize);
        } catch (Exception e) {
            log.error("[RegionChunkLoader] Ошибка сохранения чанка ({}, {}): {}", chunkX, chunkZ, e.getMessage());
        }
    }

    public void closeAll() {
        for (RegionFile rf : openRegions.values()) {
            try {
                rf.close();
            } catch (Exception ignored) {}
        }
        openRegions.clear();
    }

    @Override
    public boolean supportsParallelSaving() { return true; }

    @Override
    public boolean supportsParallelLoading() { return true; }
}
