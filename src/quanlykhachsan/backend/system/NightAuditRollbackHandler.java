package quanlykhachsan.backend.system;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

/** Resumes a failed audit from its last committed step/chunk checkpoint. */
public final class NightAuditRollbackHandler {
    private final NightAuditEngine engine;
    public NightAuditRollbackHandler() { this(new NightAuditEngine()); }
    public NightAuditRollbackHandler(NightAuditEngine engine) { this.engine = Objects.requireNonNull(engine); }
    public void recover(LocalDate auditDate) throws SQLException { engine.run(auditDate); }
}
