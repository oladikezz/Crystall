package site.deforce.SM_Clans.models;

import java.util.UUID;

public class ClanCreationProcess {
   private final UUID playerId;
   private String tag;
   private String name;
   private ClanCreationStep step;
   private long startedAt;

   public ClanCreationProcess(UUID playerId) {
      super();
      this.playerId = playerId;
      this.step = ClanCreationProcess.ClanCreationStep.WAITING_TAG;
      this.startedAt = System.currentTimeMillis();
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public String getTag() {
      return this.tag;
   }

   public void setTag(String tag) {
      this.tag = tag;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public ClanCreationStep getStep() {
      return this.step;
   }

   public void setStep(ClanCreationStep step) {
      this.step = step;
   }

   public long getStartedAt() {
      return this.startedAt;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() - this.startedAt > 120000L;
   }

   public static enum ClanCreationStep {
      WAITING_TAG,
      WAITING_NAME,
      COMPLETED;

      private ClanCreationStep() {
      }
   }
}
