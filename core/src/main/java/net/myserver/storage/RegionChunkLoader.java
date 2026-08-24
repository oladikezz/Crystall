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
import java.util.concurrent.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Высокопроизводительный загрузчик чанков в формате Region Files (32x32 чанка на файл).
 * Включает Async Write Coalescing (дедупликация и отложенная запись), Thread-Local пулы сжатия и пропуск пустых секций.
 */
public class RegionChunkLoader implements ChunkLoader {
    private static final Logger log = LoggerFactory.getLogger(RegionChunkLoader.class);
    private static final int SECTOR_HEADER_SIZE = 8192; // 1024 * 8 байт (offset: int, size: int)
    private static final int TOTAL_SECTIONS = 24;       // от Y=-64 до Y=320 (384 / 16 = 24 секции)
    private static final int BUFFER_SIZE = 262144;      // 256 КБ буфер для сжатия

    private final File regionFolder;
    private final Map<Long, RegionFile> openRegions = new ConcurrentHashMap<>();

    // Очередь асинхронной отложенной записи чанков (Coalescing Write-Behind)
    private static class PendingChunkWrite {
        final int chunkX;
        final int chunkZ;
        final byte[] data;
        final int length;

        PendingChunkWrite(int chunkX, int chunkZ, byte[] data, int length) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.data = data;
            this.length = length;
        }
    }

    private final Map<Long, PendingChunkWrite> pendingWrites = new ConcurrentHashMap<>();
    private final ExecutorService asyncDiskWriter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crystall-Async-Region-Writer");
        t.setDaemon(true);
        return t;
    });

    // Thread-Local компрессоры для исключения повторных аллокаций
    private static final ThreadLocal<Deflater> deflaterPool = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION));
    private static final ThreadLocal<Inflater> inflaterPool = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<byte[]> ioBufferPool = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    public RegionChunkLoader(String basePath) {
        this.regionFolder = new File(basePath, "regions");
        if (!this.regionFolder.exists()) {
            this.regionFolder.mkdirs();
        }

        // Фоновый цикл сброса очереди отложенной записи (flush каждые 500 мс)
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Crystall-Region-Flush-Timer");
            t.setDaemon(true);
            return t;
        }).scheduleWithFixedDelay(this::flushPendingWrites, 500, 500, TimeUnit.MILLISECONDS);
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

    private synchronized RegionFile getRegionFile(int rx, int rz) {
        long key = FastMath.packChunkPos(rx, rz);
        return openRegions.computeIfAbsent(key, k -> {
            File path = new File(regionFolder, "r." + rx + "." + rz + ".mcr");
            try {
                return new RegionFile(path);
            } catch (IOException e) {
                log.error("[RegionChunkLoader] Ошибка открытия региона ({}, {}): {}", rx, rz, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public @Nullable Chunk loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        long chunkKey = FastMath.packChunkPos(chunkX, chunkZ);
        
        // 1. Проверяем наличие еще не сброшенных на диск данных в очереди записи
        PendingChunkWrite pending = pendingWrites.get(chunkKey);
        if (pending != null) {
            try {
                Chunk deserialized = deserializeChunk(instance, chunkX, chunkZ, pending.data, pending.length);
                if (deserialized != null) {
                    return deserialized;
                }
            } catch (Exception ignored) {}
        }

        int rx = chunkX >> 5;
        int rz = chunkZ >> 5;
        int lx = chunkX & 31;
        int lz = chunkZ & 31;
        int index = lx + (lz * 32);

        RegionFile rf = getRegionFile(rx, rz);
        if (rf == null) {
            return null;
        }

        try {
            byte[] compressed = rf.readChunk(index);
            if (compressed == null) {
                return null;
            }

            return deserializeChunk(instance, chunkX, chunkZ, compressed, compressed.length);
        } catch (Exception e) {
            log.error("[RegionChunkLoader] Ошибка чтения чанка ({}, {}): {}", chunkX, chunkZ, e.getMessage());
            return null;
        }
    }

    private Chunk deserializeChunk(Instance instance, int chunkX, int chunkZ, byte[] compressed, int length) throws Exception {
        Inflater inflater = inflaterPool.get();
        inflater.reset();
        inflater.setInput(compressed, 0, length);

        byte[] rawBuffer = ioBufferPool.get();
        int decompressedSize = inflater.inflate(rawBuffer);

        ByteArrayInputStream bais = new ByteArrayInputStream(rawBuffer, 0, decompressedSize);
        DataInputStream in = new DataInputStream(bais);

        int sectionMask = in.readInt();
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            chunk = new net.minestom.server.instance.DynamicChunk(instance, chunkX, chunkZ);
        }

        for (int s = 0; s < TOTAL_SECTIONS; s++) {
            if ((sectionMask & (1 << s)) == 0) continue;

            int startY = -64 + (s * 16);
            int paletteSize = in.readInt();
            int[] palette = new int[paletteSize];
            for (int p = 0; p < paletteSize; p++) {
                palette[p] = in.readInt();
            }

            if (paletteSize == 1) {
                Block singleBlock = Block.fromStateId(palette[0]);
                if (singleBlock != null) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                chunk.setBlock(x, startY + y, z, singleBlock);
                            }
                        }
                    }
                }
            } else {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int pIdx = in.readUnsignedByte();
                            if (pIdx < paletteSize) {
                                Block b = Block.fromStateId(palette[pIdx]);
                                if (b != null) {
                                    chunk.setBlock(x, startY + y, z, b);
                                }
                            }
                        }
                    }
                }
            }
        }
        return chunk;
    }

    @Override
    public void saveChunk(@NotNull Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();

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

            // Сохраняем в очередь асинхронной отложенной записи (Write Coalescing)
            long key = FastMath.packChunkPos(chunkX, chunkZ);
            byte[] snapshot = Arrays.copyOf(compressedBuffer, compressedSize);
            pendingWrites.put(key, new PendingChunkWrite(chunkX, chunkZ, snapshot, compressedSize));
        } catch (Exception e) {
            log.error("[RegionChunkLoader] Ошибка сжатия чанка ({}, {}): {}", chunkX, chunkZ, e.getMessage());
        }
    }

    public void flushPendingWrites() {
        if (pendingWrites.isEmpty()) return;

        asyncDiskWriter.submit(() -> {
            Iterator<Map.Entry<Long, PendingChunkWrite>> it = pendingWrites.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, PendingChunkWrite> entry = it.next();
                it.remove();

                PendingChunkWrite pw = entry.getValue();
                int rx = pw.chunkX >> 5;
                int rz = pw.chunkZ >> 5;
                int lx = pw.chunkX & 31;
                int lz = pw.chunkZ & 31;
                int index = lx + (lz * 32);

                RegionFile rf = getRegionFile(rx, rz);
                if (rf != null) {
                    try {
                        rf.writeChunk(index, pw.data, pw.length);
                    } catch (IOException e) {
                        log.error("[RegionChunkLoader] Ошибка фоновой записи чанка ({}, {}): {}", pw.chunkX, pw.chunkZ, e.getMessage());
                    }
                }
            }
        });
    }

    public void closeAll() {
        flushPendingWrites();
        asyncDiskWriter.shutdown();
        try {
            asyncDiskWriter.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

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
