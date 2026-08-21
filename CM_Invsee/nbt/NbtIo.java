package net.schalker.SMPS.modules.invsee.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NbtIo {
   public static final byte TAG_END = 0;
   public static final byte TAG_BYTE = 1;
   public static final byte TAG_SHORT = 2;
   public static final byte TAG_INT = 3;
   public static final byte TAG_LONG = 4;
   public static final byte TAG_FLOAT = 5;
   public static final byte TAG_DOUBLE = 6;
   public static final byte TAG_BYTE_ARRAY = 7;
   public static final byte TAG_STRING = 8;
   public static final byte TAG_LIST = 9;
   public static final byte TAG_COMPOUND = 10;
   public static final byte TAG_INT_ARRAY = 11;
   public static final byte TAG_LONG_ARRAY = 12;

   private NbtIo() {
   }

   public static Compound readCompressed(Path path) throws IOException {
      try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
         return readRoot(new DataInputStream(input));
      }
   }

   public static void writeCompressed(Path path, Compound root) throws IOException {
      try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
         writeRoot(new DataOutputStream(output), root);
      }
   }

   public static Compound read(byte[] bytes) throws IOException {
      try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
         return readRoot(input);
      }
   }

   public static byte[] write(Compound root) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         writeRoot(output, root);
      }
      return bytes.toByteArray();
   }

   private static Compound readRoot(DataInput input) throws IOException {
      byte type = input.readByte();
      if (type != TAG_COMPOUND) {
         throw new IOException("Root NBT tag must be a compound, got " + type);
      }
      readString(input);
      return (Compound) readPayload(input, TAG_COMPOUND);
   }

   private static void writeRoot(DataOutput output, Compound root) throws IOException {
      output.writeByte(TAG_COMPOUND);
      writeString(output, "");
      writePayload(output, TAG_COMPOUND, root);
   }

   private static Tag readPayload(DataInput input, byte type) throws IOException {
      return switch (type) {
         case TAG_BYTE -> new ByteTag(input.readByte());
         case TAG_SHORT -> new ShortTag(input.readShort());
         case TAG_INT -> new IntTag(input.readInt());
         case TAG_LONG -> new LongTag(input.readLong());
         case TAG_FLOAT -> new FloatTag(input.readFloat());
         case TAG_DOUBLE -> new DoubleTag(input.readDouble());
         case TAG_BYTE_ARRAY -> readByteArray(input);
         case TAG_STRING -> new StringTag(readString(input));
         case TAG_LIST -> readList(input);
         case TAG_COMPOUND -> readCompound(input);
         case TAG_INT_ARRAY -> readIntArray(input);
         case TAG_LONG_ARRAY -> readLongArray(input);
         default -> throw new IOException("Unsupported NBT tag type " + type);
      };
   }

   private static Compound readCompound(DataInput input) throws IOException {
      Compound compound = new Compound();
      while (true) {
         byte type = input.readByte();
         if (type == TAG_END) {
            return compound;
         }
         String name = readString(input);
         compound.put(name, readPayload(input, type));
      }
   }

   private static ListTag readList(DataInput input) throws IOException {
      byte elementType = input.readByte();
      int size = input.readInt();
      if (size < 0) {
         throw new IOException("Negative NBT list size " + size);
      }
      if (elementType == TAG_END && size > 0) {
         throw new IOException("Non-empty NBT list cannot use TAG_End elements");
      }
      List<Tag> value = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
         value.add(readPayload(input, elementType));
      }
      return new ListTag(elementType, value);
   }

   private static ByteArrayTag readByteArray(DataInput input) throws IOException {
      int size = input.readInt();
      if (size < 0) {
         throw new IOException("Negative NBT byte-array size " + size);
      }
      byte[] value = new byte[size];
      input.readFully(value);
      return new ByteArrayTag(value);
   }

   private static IntArrayTag readIntArray(DataInput input) throws IOException {
      int size = input.readInt();
      if (size < 0) {
         throw new IOException("Negative NBT int-array size " + size);
      }
      int[] value = new int[size];
      for (int i = 0; i < size; i++) {
         value[i] = input.readInt();
      }
      return new IntArrayTag(value);
   }

   private static LongArrayTag readLongArray(DataInput input) throws IOException {
      int size = input.readInt();
      if (size < 0) {
         throw new IOException("Negative NBT long-array size " + size);
      }
      long[] value = new long[size];
      for (int i = 0; i < size; i++) {
         value[i] = input.readLong();
      }
      return new LongArrayTag(value);
   }

   private static void writePayload(DataOutput output, byte type, Tag tag) throws IOException {
      if (tag.type() != type) {
         throw new IOException("NBT tag type mismatch: expected " + type + ", got " + tag.type());
      }

      switch (type) {
         case TAG_BYTE -> output.writeByte(((ByteTag) tag).value());
         case TAG_SHORT -> output.writeShort(((ShortTag) tag).value());
         case TAG_INT -> output.writeInt(((IntTag) tag).value());
         case TAG_LONG -> output.writeLong(((LongTag) tag).value());
         case TAG_FLOAT -> output.writeFloat(((FloatTag) tag).value());
         case TAG_DOUBLE -> output.writeDouble(((DoubleTag) tag).value());
         case TAG_BYTE_ARRAY -> writeByteArray(output, (ByteArrayTag) tag);
         case TAG_STRING -> writeString(output, ((StringTag) tag).value());
         case TAG_LIST -> writeList(output, (ListTag) tag);
         case TAG_COMPOUND -> writeCompound(output, (Compound) tag);
         case TAG_INT_ARRAY -> writeIntArray(output, (IntArrayTag) tag);
         case TAG_LONG_ARRAY -> writeLongArray(output, (LongArrayTag) tag);
         default -> throw new IOException("Unsupported NBT tag type " + type);
      }
   }

   private static void writeCompound(DataOutput output, Compound compound) throws IOException {
      for (Map.Entry<String, Tag> entry : compound.value().entrySet()) {
         Tag tag = entry.getValue();
         output.writeByte(tag.type());
         writeString(output, entry.getKey());
         writePayload(output, tag.type(), tag);
      }
      output.writeByte(TAG_END);
   }

   private static void writeList(DataOutput output, ListTag list) throws IOException {
      output.writeByte(list.elementType());
      output.writeInt(list.value().size());
      for (Tag tag : list.value()) {
         writePayload(output, list.elementType(), tag);
      }
   }

   private static void writeByteArray(DataOutput output, ByteArrayTag tag) throws IOException {
      output.writeInt(tag.value().length);
      output.write(tag.value());
   }

   private static void writeIntArray(DataOutput output, IntArrayTag tag) throws IOException {
      output.writeInt(tag.value().length);
      for (int value : tag.value()) {
         output.writeInt(value);
      }
   }

   private static void writeLongArray(DataOutput output, LongArrayTag tag) throws IOException {
      output.writeInt(tag.value().length);
      for (long value : tag.value()) {
         output.writeLong(value);
      }
   }

   private static String readString(DataInput input) throws IOException {
      int length = input.readUnsignedShort();
      byte[] bytes = new byte[length];
      input.readFully(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
   }

   private static void writeString(DataOutput output, String value) throws IOException {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      if (bytes.length > 65535) {
         throw new IOException("NBT string is too long: " + bytes.length + " bytes");
      }
      output.writeShort(bytes.length);
      output.write(bytes);
   }

   public sealed interface Tag permits ByteTag, ShortTag, IntTag, LongTag, FloatTag, DoubleTag,
      ByteArrayTag, StringTag, ListTag, Compound, IntArrayTag, LongArrayTag {
      byte type();

      Tag copy();
   }

   public record ByteTag(byte value) implements Tag {
      @Override
      public byte type() {
         return TAG_BYTE;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record ShortTag(short value) implements Tag {
      @Override
      public byte type() {
         return TAG_SHORT;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record IntTag(int value) implements Tag {
      @Override
      public byte type() {
         return TAG_INT;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record LongTag(long value) implements Tag {
      @Override
      public byte type() {
         return TAG_LONG;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record FloatTag(float value) implements Tag {
      @Override
      public byte type() {
         return TAG_FLOAT;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record DoubleTag(double value) implements Tag {
      @Override
      public byte type() {
         return TAG_DOUBLE;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public record StringTag(String value) implements Tag {
      @Override
      public byte type() {
         return TAG_STRING;
      }

      @Override
      public Tag copy() {
         return this;
      }
   }

   public static final class ByteArrayTag implements Tag {
      private final byte[] value;

      public ByteArrayTag(byte[] value) {
         this.value = Arrays.copyOf(value, value.length);
      }

      public byte[] value() {
         return this.value;
      }

      @Override
      public byte type() {
         return TAG_BYTE_ARRAY;
      }

      @Override
      public Tag copy() {
         return new ByteArrayTag(this.value);
      }
   }

   public static final class IntArrayTag implements Tag {
      private final int[] value;

      public IntArrayTag(int[] value) {
         this.value = Arrays.copyOf(value, value.length);
      }

      public int[] value() {
         return this.value;
      }

      @Override
      public byte type() {
         return TAG_INT_ARRAY;
      }

      @Override
      public Tag copy() {
         return new IntArrayTag(this.value);
      }
   }

   public static final class LongArrayTag implements Tag {
      private final long[] value;

      public LongArrayTag(long[] value) {
         this.value = Arrays.copyOf(value, value.length);
      }

      public long[] value() {
         return this.value;
      }

      @Override
      public byte type() {
         return TAG_LONG_ARRAY;
      }

      @Override
      public Tag copy() {
         return new LongArrayTag(this.value);
      }
   }

   public static final class ListTag implements Tag {
      private final byte elementType;
      private final List<Tag> value;

      public ListTag(byte elementType, List<Tag> value) {
         this.elementType = elementType;
         this.value = new ArrayList<>(value);
      }

      public byte elementType() {
         return this.elementType;
      }

      public List<Tag> value() {
         return this.value;
      }

      @Override
      public byte type() {
         return TAG_LIST;
      }

      @Override
      public Tag copy() {
         List<Tag> copied = new ArrayList<>(this.value.size());
         for (Tag tag : this.value) {
            copied.add(tag.copy());
         }
         return new ListTag(this.elementType, copied);
      }
   }

   public static final class Compound implements Tag {
      private final LinkedHashMap<String, Tag> value;

      public Compound() {
         this.value = new LinkedHashMap<>();
      }

      private Compound(LinkedHashMap<String, Tag> value) {
         this.value = value;
      }

      public Map<String, Tag> value() {
         return this.value;
      }

      public Tag get(String key) {
         return this.value.get(key);
      }

      public boolean contains(String key) {
         return this.value.containsKey(key);
      }

      public void put(String key, Tag tag) {
         this.value.put(key, tag);
      }

      public Tag remove(String key) {
         return this.value.remove(key);
      }

      public int getInt(String key, int fallback) {
         Tag tag = this.value.get(key);
         if (tag instanceof IntTag intTag) {
            return intTag.value();
         }
         if (tag instanceof ShortTag shortTag) {
            return shortTag.value();
         }
         if (tag instanceof ByteTag byteTag) {
            return byteTag.value();
         }
         return fallback;
      }

      public ListTag getList(String key) {
         Tag tag = this.value.get(key);
         return tag instanceof ListTag listTag ? listTag : null;
      }

      @Override
      public byte type() {
         return TAG_COMPOUND;
      }

      @Override
      public Compound copy() {
         LinkedHashMap<String, Tag> copied = new LinkedHashMap<>();
         for (Map.Entry<String, Tag> entry : this.value.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().copy());
         }
         return new Compound(copied);
      }
   }
}
