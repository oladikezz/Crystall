package net.schalker.SMPS.modules.trafficoptimizer;

public enum OptimizationLevel {
   NONE(0, 0, 0, 0),
   LIGHT(1, 2, 1, 2),
   MEDIUM(2, 4, 2, 4),
   AGGRESSIVE(3, 7, 4, 7);

   private final int rank;
   private final int viewReduction;
   private final int simulationReduction;
   private final int sendReduction;

   OptimizationLevel(int rank, int viewReduction, int simulationReduction, int sendReduction) {
      this.rank = rank;
      this.viewReduction = viewReduction;
      this.simulationReduction = simulationReduction;
      this.sendReduction = sendReduction;
   }

   public int getRank() {
      return this.rank;
   }

   public int getViewReduction() {
      return this.viewReduction;
   }

   public int getSimulationReduction() {
      return this.simulationReduction;
   }

   public int getSendReduction() {
      return this.sendReduction;
   }

   public boolean isHeavierThan(OptimizationLevel other) {
      return this.rank > other.rank;
   }

   public OptimizationLevel oneStepLighter() {
      return switch (this) {
         case AGGRESSIVE -> MEDIUM;
         case MEDIUM -> LIGHT;
         case LIGHT, NONE -> NONE;
      };
   }
}
