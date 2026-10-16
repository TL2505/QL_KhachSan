package quanlykhachsan.backend.customer;

import java.nio.file.*;
import java.sql.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Explicit maintenance-mode backfill; no data changes occur during normal startup. */
public final class IdentityVaultMigration {
    private IdentityVaultMigration() { }
    public static void requireReady() throws SQLException {
        GuestIdentityVaultDao.configured();
        try(var c=DatabaseConnection.getConnection();var q=c.createStatement();var rows=q.executeQuery("SELECT COUNT(*) FROM customers WHERE identity_card IS NOT NULL")){
            rows.next();if(rows.getLong(1)!=0)throw new IllegalStateException("Identity backfill required before starting writers");
        }
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND CONSTRAINT_NAME='chk_no_plain_identity'");var rows=q.executeQuery()){
            if(!rows.next())throw new IllegalStateException("Identity cutover constraint is required before starting writers");
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !"--apply".equals(args[0]))
            throw new IllegalArgumentException("Back up the database, stop writers, supply approved keys, then use --apply");
        var vault=GuestIdentityVaultDao.configured(); // Fail before DDL if keys are missing.
        try(var c=DatabaseConnection.getConnection()){
            applySchema(c);
            int migrated=backfill(c,vault);
            finalizeSchema(c);
            System.out.println("Identity backfill completed; records="+migrated);
        } finally {DatabaseConnection.close();}
    }
    public static void applySchema(Connection c) throws Exception {
        String sql=Files.readString(Path.of("database/migrations/v3_1_biometric_vault.sql"));
        try(var statement=c.createStatement()){
            for(String command:sql.replaceAll("(?m)^--.*$", "").split(";"))
                if(!command.isBlank())statement.execute(command);
        }
    }
    public static void finalizeSchema(Connection c) throws SQLException {
        try(var q=c.prepareStatement("SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND CONSTRAINT_NAME='chk_no_plain_identity'");var r=q.executeQuery()){
            if(r.next())return;
        }
        try(var q=c.createStatement()){q.execute("ALTER TABLE customers ADD CONSTRAINT chk_no_plain_identity CHECK(identity_card IS NULL)");}
    }
    public static int backfill(Connection c,GuestIdentityVaultDao vault) throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Migration must own the transaction");
        c.setAutoCommit(false);
        try{
            int count=0;
            try(var query=c.prepareStatement("SELECT id,identity_card FROM customers WHERE identity_card IS NOT NULL ORDER BY id FOR UPDATE");var rows=query.executeQuery()){
                while(rows.next()){
                    String identity=rows.getString(2);
                    if(identity.isBlank()){
                        try(var clear=c.prepareStatement("UPDATE customers SET identity_card=NULL WHERE id=?")){clear.setInt(1,rows.getInt(1));clear.executeUpdate();}
                    }else{vault.save(c,rows.getInt(1),identity,null,null);count++;}
                }
            }
            c.commit();return count;
        }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
}
