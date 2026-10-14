package quanlykhachsan.backend.system;

/** The seven checkpointed operations in the Phase 1 night audit WBS. */
public enum NightAuditStep {
    INITIATE_FREEZE(1), ROLL_ROOM_RATES(2), PROCESS_NO_SHOWS(3),
    AUXILIARY_ROLLUP(4), RECONCILE_LEDGER(5), CREATE_DBR(6), ADVANCE_DATE(7);
    private final int number;
    NightAuditStep(int number) { this.number = number; }
    public int number() { return number; }
}
