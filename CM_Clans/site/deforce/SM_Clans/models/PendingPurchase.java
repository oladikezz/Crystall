package site.deforce.SM_Clans.models;

public class PendingPurchase {
   private final Type type;
   private final String param;
   private final long cost;
   private final String label;

   public PendingPurchase(Type type, String param, long cost, String label) {
      super();
      this.type = type;
      this.param = param;
      this.cost = cost;
      this.label = label;
   }

   public Type getType() {
      return this.type;
   }

   public String getParam() {
      return this.param;
   }

   public long getCost() {
      return this.cost;
   }

   public String getLabel() {
      return this.label;
   }

   public static enum Type {
      BUY_SLOTS,
      CHANGE_NAME,
      CHANGE_TAG,
      CHANGE_DESCRIPTION,
      CHANGE_BANNER_COLOR,
      SET_FLAG;

      private Type() {
      }
   }
}
