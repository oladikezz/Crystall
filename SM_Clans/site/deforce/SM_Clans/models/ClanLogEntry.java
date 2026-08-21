package site.deforce.SM_Clans.models;

public class ClanLogEntry {
   private final long id;
   private final long timestamp;
   private final String action;
   private final String actorName;
   private final String clanTag;
   private final String target;
   private final Long amount;
   private final Long balance;

   public ClanLogEntry(long id, long timestamp, String action, String actorName, String clanTag, String target, Long amount, Long balance) {
      super();
      this.id = id;
      this.timestamp = timestamp;
      this.action = action;
      this.actorName = actorName;
      this.clanTag = clanTag;
      this.target = target;
      this.amount = amount;
      this.balance = balance;
   }

   public long getId() {
      return this.id;
   }

   public long getTimestamp() {
      return this.timestamp;
   }

   public String getAction() {
      return this.action;
   }

   public String getActorName() {
      return this.actorName;
   }

   public String getClanTag() {
      return this.clanTag;
   }

   public String getTarget() {
      return this.target;
   }

   public Long getAmount() {
      return this.amount;
   }

   public Long getBalance() {
      return this.balance;
   }
}
