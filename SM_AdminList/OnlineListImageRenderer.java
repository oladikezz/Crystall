package net.schalker.SMPS.modules.adminlist;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

public class OnlineListImageRenderer {
   private static final int WIDTH = 420;
   private static final int ROW_HEIGHT = 22;
   private static final int PADDING = 10;
   private static final int SEPARATOR_HEIGHT = 8;

   private static final Color BG_COLOR = new Color(44, 44, 44, 230);
   private static final Color NAME_COLOR = new Color(245, 245, 245);
   private static final Color DEFAULT_ADMIN_COLOR = new Color(255, 85, 85);
   private static final Color SEPARATOR_COLOR = new Color(80, 80, 80);

   private Font cachedFont;
   private String cachedZipPath;
   private String cachedEntryPath;

   public byte[] render(
      List<AdminListWebhook.OnlineEntry> entries,
      int maxPlayers,
      String titlePattern,
      String fontZipPath,
      String fontEntryPath
   ) {
      int count = entries == null ? 0 : entries.size();

      boolean hasAdmins = false;
      boolean hasNonAdmins = false;
      if (entries != null) {
         for (AdminListWebhook.OnlineEntry entry : entries) {
            if (entry.isAdmin()) hasAdmins = true;
            else hasNonAdmins = true;
         }
      }
      boolean needsSeparator = hasAdmins && hasNonAdmins;
      int separatorExtra = needsSeparator ? SEPARATOR_HEIGHT : 0;

      int height = Math.max(PADDING * 2 + ROW_HEIGHT, PADDING * 2 + Math.max(1, count) * ROW_HEIGHT + separatorExtra);

      BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g.setColor(BG_COLOR);
      g.fillRoundRect(0, 0, WIDTH, height, 14, 14);

      g.setFont(this.resolveMinecraftFont(fontZipPath, fontEntryPath));
      FontMetrics metrics = g.getFontMetrics();

      if (count == 0) {
         g.setColor(NAME_COLOR);
         g.drawString("-", PADDING, PADDING + metrics.getAscent());
      } else {
         boolean separatorDrawn = false;
         int yOffset = 0;

         for (int i = 0; i < entries.size(); i++) {
            AdminListWebhook.OnlineEntry entry = entries.get(i);

            if (!separatorDrawn && needsSeparator && !entry.isAdmin()) {
               int separatorY = PADDING + i * ROW_HEIGHT + yOffset + SEPARATOR_HEIGHT / 2;
               g.setColor(SEPARATOR_COLOR);
               g.drawLine(PADDING, separatorY, WIDTH - PADDING, separatorY);
               yOffset += SEPARATOR_HEIGHT;
               separatorDrawn = true;
            }

            int yTop = PADDING + i * ROW_HEIGHT + yOffset;
            int yMid = yTop + (ROW_HEIGHT / 2);

            if (entry.isAdmin()) {
               Color adminColor = entry.prefixColor() != null ? entry.prefixColor() : DEFAULT_ADMIN_COLOR;
               g.setColor(adminColor);
            } else {
               g.setColor(NAME_COLOR);
            }

            int textY = yMid + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(entry.name(), PADDING, textY);
         }
      }

      g.dispose();

      try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
         ImageIO.write(image, "png", output);
         return output.toByteArray();
      } catch (Exception ignored) {
         return null;
      }
   }

   private Font resolveMinecraftFont(String fontZipPath, String fontEntryPath) {
      String safeZipPath = fontZipPath == null ? "" : fontZipPath.trim();
      String safeEntryPath = fontEntryPath == null ? "" : fontEntryPath.trim();

      if (this.cachedFont != null
         && safeZipPath.equals(this.cachedZipPath)
         && safeEntryPath.equals(this.cachedEntryPath)) {
         return this.cachedFont;
      }

      Font zipFont = this.loadFontFromZip(safeZipPath, safeEntryPath);
      if (zipFont != null) {
         this.cachedFont = zipFont;
         this.cachedZipPath = safeZipPath;
         this.cachedEntryPath = safeEntryPath;
         return this.cachedFont;
      }

      String[] candidates = {"Minecraft", "Mojangles", "Minecraftia"};
      for (String family : candidates) {
         Font candidate = new Font(family, Font.PLAIN, 16);
         String resolvedFamily = candidate.getFamily();
         if (!"Dialog".equalsIgnoreCase(resolvedFamily) && !"SansSerif".equalsIgnoreCase(resolvedFamily)) {
            this.cachedFont = candidate;
            this.cachedZipPath = safeZipPath;
            this.cachedEntryPath = safeEntryPath;
            return this.cachedFont;
         }
      }

      this.cachedFont = new Font("Monospaced", Font.PLAIN, 16);
      this.cachedZipPath = safeZipPath;
      this.cachedEntryPath = safeEntryPath;
      return this.cachedFont;
   }

   private Font loadFontFromZip(String fontZipPath, String fontEntryPath) {
      if (fontZipPath == null || fontZipPath.isBlank()) {
         return null;
      }

      try {
         Path zipPath = Paths.get(fontZipPath);
         if (!zipPath.isAbsolute()) {
            zipPath = Paths.get(System.getProperty("user.dir")).resolve(zipPath).normalize();
         }
         if (!Files.exists(zipPath)) {
            return null;
         }

         try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry targetEntry = null;
            if (fontEntryPath != null && !fontEntryPath.isBlank()) {
               targetEntry = zipFile.getEntry(fontEntryPath);
            }

            if (targetEntry == null) {
               var entries = zipFile.entries();
               while (entries.hasMoreElements()) {
                  ZipEntry entry = entries.nextElement();
                  if (entry.isDirectory()) {
                     continue;
                  }
                  String name = entry.getName().toLowerCase();
                  if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                     targetEntry = entry;
                     if (name.contains("minecraft") || name.contains("mojang")) {
                        break;
                     }
                  }
               }
            }

            if (targetEntry == null) {
               return null;
            }

            try (InputStream input = zipFile.getInputStream(targetEntry)) {
               return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, 16f);
            }
         }
      } catch (Exception ignored) {
         return null;
      }
   }

}
