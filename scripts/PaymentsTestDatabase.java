import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Owns only a randomly named disposable test schema, verified by a private marker. */
public class PaymentsTestDatabase {
    public static void main(String[] args)throws Exception {
        String schema=System.getenv("DB_NAME"),owner=System.getenv("HOTEL_PAYMENT_TEST_OWNER");
        if(schema==null||!schema.matches("hotel_payments_[a-f0-9]{12}_test")||owner==null||!owner.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Explicit disposable schema owner required");
        String password=System.getenv().getOrDefault("DB_PASSWORD","");
        try(var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+Integer.parseInt(System.getenv().getOrDefault("DB_PORT","3306"))+"/?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh",System.getenv("DB_USER"),password)){
            if(args.length!=1)throw new IllegalArgumentException();
            if(args[0].equals("create")){
                try(var s=c.createStatement()){s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");}c.setCatalog(schema);
                try(var s=c.createStatement()){s.execute("CREATE TABLE _payment_test_owner(id CHAR(32) PRIMARY KEY)");s.execute("INSERT INTO _payment_test_owner VALUES('"+owner+"')");}
                String base=Files.readString(Path.of("database/hotel.sql"));int start=base.indexOf("CREATE TABLE IF NOT EXISTS roles"),end=base.indexOf("-- SEED DATA FROM PRODUCTION");if(start<0||end<start)throw new IllegalStateException("Schema boundaries missing");
                execute(c,base.substring(start,end));execute(c,Files.readString(Path.of("database/bootstrap/prerequisites.sql")));
                for(String migration:Files.readAllLines(Path.of("database/bootstrap/migrations.txt"))){if(migration.isBlank())continue;if(!migration.matches("[A-Za-z0-9_]+\\.sql"))throw new IllegalArgumentException();execute(c,Files.readString(Path.of("database/migrations",migration)).replace("USE hotel_prod_db;",""));}
                quanlykhachsan.backend.utils.UpdateDbUtility.migrate(c);
                System.out.println("Disposable payment schema created: "+schema);
            }else if(args[0].equals("drop")){
                c.setCatalog(schema);try(var s=c.createStatement();var r=s.executeQuery("SELECT id FROM _payment_test_owner")){if(!r.next()||!owner.equals(r.getString(1))||r.next())throw new IllegalStateException("Test ownership marker mismatch; refusing cleanup");}
                try(var s=c.createStatement()){s.execute("DROP DATABASE `"+schema+"`");}System.out.println("Owned disposable payment schema removed: "+schema);
            }else throw new IllegalArgumentException();
        }catch(SQLException e){System.err.println("Test database operation failed; SQLState="+e.getSQLState()+", code="+e.getErrorCode());System.exit(2);}
    }
    private static void execute(Connection c,String sql)throws SQLException {
        String delimiter=";";StringBuilder pending=new StringBuilder();try(var s=c.createStatement()){
            for(String line:sql.split("\\R")){String trimmed=line.trim();if(trimmed.isBlank()||trimmed.startsWith("--")||trimmed.startsWith("#"))continue;if(trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")){delimiter=trimmed.substring(10).trim();continue;}pending.append(line).append('\n');String command=pending.toString().trim();if(command.endsWith(delimiter)){s.execute(command.substring(0,command.length()-delimiter.length()));pending.setLength(0);}}
            if(!pending.toString().isBlank())throw new SQLException("Unterminated bootstrap SQL");
        }
    }
}
