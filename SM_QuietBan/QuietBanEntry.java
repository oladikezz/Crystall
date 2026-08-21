package net.schalker.SMPS.modules.quietban;

import java.util.Locale;
import java.util.UUID;

public record QuietBanEntry(String id,
                            UUID uuid,
                            String playerName,
                            QuietBanLevel level,
                            boolean ipLock,
                            String ip,
                            String reason,
                            String issuedBy,
                            long issuedAt,
                            long expiresAt,
                            String source) {

   public static QuietBanEntry create(UUID uuid, String playerName, QuietBanLevel level, boolean ipLock,
                                      String ip, String reason, String issuedBy, long issuedAt, long expiresAt) {
      return new QuietBanEntry(UUID.randomUUID().toString(), uuid, playerName, level, ipLock, ip,
         reason, issuedBy, issuedAt, expiresAt, null);
   }

   public static QuietBanEntry derive(QuietBanEntry origin, UUID uuid, String playerName, String ip, long now) {
      return new QuietBanEntry(UUID.randomUUID().toString(), uuid, playerName, origin.level(), true, ip,
         origin.reason(), origin.issuedBy(), now, origin.expiresAt(), origin.id());
   }

   public String playerNameLower() {
      return this.playerName == null ? "" : this.playerName.toLowerCase(Locale.ROOT);
   }

   public boolean isPermanent() {
      return this.expiresAt <= 0L;
   }

   public boolean isExpired(long now) {
      return !isPermanent() && now >= this.expiresAt;
   }

   public boolean isIpLinked() {
      return this.source != null && !this.source.isEmpty();
   }

   public QuietBanEntry withUuid(UUID value) {
      return new QuietBanEntry(this.id, value, this.playerName, this.level, this.ipLock, this.ip,
         this.reason, this.issuedBy, this.issuedAt, this.expiresAt, this.source);
   }

   public QuietBanEntry withPlayerName(String value) {
      return new QuietBanEntry(this.id, this.uuid, value, this.level, this.ipLock, this.ip,
         this.reason, this.issuedBy, this.issuedAt, this.expiresAt, this.source);
   }

   public QuietBanEntry withIp(String value) {
      return new QuietBanEntry(this.id, this.uuid, this.playerName, this.level, this.ipLock, value,
         this.reason, this.issuedBy, this.issuedAt, this.expiresAt, this.source);
   }
}
