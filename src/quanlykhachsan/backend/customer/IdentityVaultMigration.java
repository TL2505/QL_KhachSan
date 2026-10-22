package quanlykhachsan.backend.customer;

import java.nio.file.*;
import java.sql.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Explicit maintenance migration. Startup validates readiness and never erases identities. */
public final class IdentityVaultMigration {
    private IdentityVaultMigration(){}
    public static void requireReady(){
        GuestIdentityVaultDao.configured(); // Validate secure key configuration before any database access.
        try(var c=DatabaseConnection.getConnection()){verifyReady(c);}
        catch(SQLException ex){throw new IllegalStateException("Identity vault readiness could not be verified",ex);}
    }
    public static void verifyReady(Connection c)throws SQLException {
        try(var q=c.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM customers WHERE identity_card IS NOT NULL")){
            if(!r.next()||r.getLong(1)>0)throw new IllegalStateException("Plain identities require the explicit backed-up IdentityVaultMigration --apply maintenance workflow");
        }
        long records = GuestIdentityVaultDao.configured().verifyExistingRecords(c);
        System.out.println("[VAULT] AES/HMAC verified records=" + records
                + (records == 0 ? "; empty vault: historical key ownership cannot be proven" : ""));
        finalizeSchema(c);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!"--apply".equals(args[0]))throw new IllegalArgumentException("Back up the database, stop writers, supply approved keys, then use --apply");
        var vault=GuestIdentityVaultDao.configured();
        try(var c=DatabaseConnection.getConnection()){
            applySchema(c);int migrated=backfill(c,vault);finalizeSchema(c);
            System.out.println("Identity backfill completed; records="+migrated);
        }finally{DatabaseConnection.close();}
    }
    public static void applySchema(Connection c)throws Exception {
        String sql=Files.readString(Path.of("database/migrations/v3_1_biometric_vault.sql"));
        try(var statement=c.createStatement()){
            for(String command:sql.replaceAll("(?m)^--.*$", "").split(";"))if(!command.isBlank())statement.execute(command);
        }
    }
    public static void finalizeSchema(Connection c)throws SQLException {
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
