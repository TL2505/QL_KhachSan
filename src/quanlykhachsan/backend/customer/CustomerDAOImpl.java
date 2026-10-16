package quanlykhachsan.backend.customer;

import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Identity writes commit with their vault record; plaintext never enters SQL parameters. */
public class CustomerDAOImpl implements CustomerDAO {
    @Override public void addCustomer(Customer customer) throws Exception {addAndReturnId(customer);}
    @Override public boolean insert(Customer customer) throws Exception {addAndReturnId(customer);return true;}
    @Override public int addAndReturnId(Customer customer) throws SQLException {
        String identity=customer.getIdentityCard();
        var vault=hasIdentity(identity)?GuestIdentityVaultDao.configured():null;
        try(var c=DatabaseConnection.getConnection()){
            String phoneLock=customer.getPhone()==null?"":customer.getPhone().trim();
            GuestIngestionService.lock(c,phoneLock,true);
            c.setAutoCommit(false);
            try{
                rejectDuplicate(c,0,customer.getPhone(),vault==null?null:vault.blindIndex(identity));
                int id;
                try(var q=c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone,email,address,is_vip,loyalty_level,date_of_birth) VALUES (?,NULL,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                    q.setString(1,customer.getFullName());q.setString(2,customer.getPhone());q.setString(3,customer.getEmail());
                    q.setString(4,customer.getAddress());q.setBoolean(5,customer.isVip());
                    q.setString(6,customer.getLoyaltyLevel()==null||customer.getLoyaltyLevel().isBlank()?"Member":customer.getLoyaltyLevel());
                    q.setString(7,customer.getDateOfBirth());
                    q.executeUpdate();try(var r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Customer insert returned no ID");id=r.getInt(1);}
                }
                if(vault!=null)vault.save(c,id,identity,null,null);
                c.commit();return id;
            }catch(SQLException | RuntimeException e){c.rollback();
                if(e instanceof SQLException sql && (sql.getErrorCode()==1062 || "23000".equals(sql.getSQLState())))throw new IllegalStateException("Customer identity or phone already exists");
                throw e;}
            finally{c.setAutoCommit(true);GuestIngestionService.lock(c,phoneLock,false);}
        }
    }
    @Override public int createCustomer(String name,String phone,String identity,String email,String level) throws SQLException {
        Customer customer=new Customer();customer.setFullName(name==null?"":name.trim());
        customer.setPhone(phone==null?"":phone.trim());customer.setIdentityCard(identity);customer.setEmail(email);
        customer.setLoyaltyLevel(level);return addAndReturnId(customer);
    }
    @Override public void updateCustomer(Customer customer) throws SQLException {
        String identity=customer.getIdentityCard();var vault=hasIdentity(identity)?GuestIdentityVaultDao.configured():null;
        try(var c=DatabaseConnection.getConnection()){
            String phoneLock=customer.getPhone()==null?"":customer.getPhone().trim();
            GuestIngestionService.lock(c,phoneLock,true);
            c.setAutoCommit(false);
            try{
                rejectDuplicate(c,customer.getId(),customer.getPhone(),vault==null?null:vault.blindIndex(identity));
                try(var q=c.prepareStatement("UPDATE customers SET full_name=?,phone=?,email=?,address=?,is_vip=?,loyalty_points=?,total_loyalty_points=?,loyalty_level=?,date_of_birth=? WHERE id=?")){
                    q.setString(1,customer.getFullName());q.setString(2,customer.getPhone());q.setString(3,customer.getEmail());
                    q.setString(4,customer.getAddress());q.setBoolean(5,customer.isVip());q.setInt(6,customer.getLoyaltyPoints());
                    q.setInt(7,customer.getTotalLoyaltyPoints());q.setString(8,customer.getLoyaltyLevel());q.setString(9,customer.getDateOfBirth());q.setInt(10,customer.getId());q.executeUpdate();
                }
                if(vault!=null)vault.save(c,customer.getId(),identity,null,null);
                c.commit();
            }catch(SQLException | RuntimeException e){c.rollback();
                if(e instanceof SQLException sql && (sql.getErrorCode()==1062 || "23000".equals(sql.getSQLState())))throw new IllegalStateException("Customer identity or phone already exists");
                throw e;}
            finally{c.setAutoCommit(true);GuestIngestionService.lock(c,phoneLock,false);}
        }
    }
    private static boolean hasIdentity(String identity){return identity!=null&&!identity.isBlank();}
    private static void rejectDuplicate(Connection c,int excluded,String phone,byte[] hash) throws SQLException {
        try(var q=c.prepareStatement("SELECT id FROM customers WHERE id<>? AND ((phone=? AND phone<>'') OR identity_hash=?) LIMIT 1")){
            q.setInt(1,excluded);q.setString(2,phone);q.setBytes(3,hash);
            try(var r=q.executeQuery()){if(r.next())throw new IllegalStateException("Customer identity or phone already exists");}
        }
    }
    @Override public void deleteCustomer(Customer customer) throws SQLException {
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("DELETE FROM customers WHERE id=?")){q.setInt(1,customer.getId());q.executeUpdate();}
    }
    @Override public void updateLoyaltyPoints(int customer,int current,int total,String level) throws SQLException {
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("UPDATE customers SET loyalty_points=loyalty_points+?,total_loyalty_points=total_loyalty_points+?,loyalty_level=? WHERE id=?")){
            q.setInt(1,current);q.setInt(2,total);q.setString(3,level);q.setInt(4,customer);q.executeUpdate();
        }
    }
    @Override public ArrayList<Customer> selectCustomer(){return load(null);}
    @Override public List<Customer> findAll(){return selectCustomer();}
    @Override public Customer findById(int id){var rows=load(id);return rows.isEmpty()?null:rows.get(0);}
    @Override public void comboBoxCustomer(){ }
    private ArrayList<Customer> load(Integer id){
        var rows=new ArrayList<Customer>();
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT * FROM customers"+(id==null?"":" WHERE id=?"))){
            if(id!=null)q.setInt(1,id);
            try(var r=q.executeQuery()){while(r.next()){
                var customer=new Customer();customer.setId(r.getInt("id"));customer.setFullName(r.getString("full_name"));
                customer.setPhone(r.getString("phone"));customer.setEmail(r.getString("email"));customer.setAddress(r.getString("address"));
                customer.setDateOfBirth(r.getString("date_of_birth"));
                customer.setVip(r.getBoolean("is_vip"));customer.setLoyaltyPoints(r.getInt("loyalty_points"));
                customer.setTotalLoyaltyPoints(r.getInt("total_loyalty_points"));customer.setLoyaltyLevel(r.getString("loyalty_level"));
                if(r.getBytes("identity_hash")!=null)customer.setIdentityCard(GuestIdentityVaultDao.configured().readIdentity(c,customer.getId()));
                rows.add(customer);
            }}return rows;
        }catch(SQLException e){throw new IllegalStateException("Customer lookup unavailable",e);}
    }
    @Override public List<Map<String,Object>> searchCustomers(String keyword){
        boolean filtered=keyword!=null&&!keyword.isBlank();var rows=new ArrayList<Map<String,Object>>();
        var vault=filtered?GuestIdentityVaultDao.configured():null;
        String sql="SELECT id,full_name,phone,address,date_of_birth,identity_hash,loyalty_level,created_at FROM customers"+
                (filtered?" WHERE full_name LIKE ? OR phone LIKE ? OR identity_hash=?":"")+" ORDER BY id DESC LIMIT 50";
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement(sql)){
            if(filtered){q.setString(1,"%"+keyword.trim()+"%");q.setString(2,"%"+keyword.trim()+"%");q.setBytes(3,vault.blindIndex(keyword));}
            try(var r=q.executeQuery()){while(r.next()){
                var row=new LinkedHashMap<String,Object>();int id=r.getInt("id");
                row.put("id",id);row.put("full_name",r.getString("full_name"));row.put("phone",r.getString("phone"));
                row.put("identity_card",r.getBytes("identity_hash")==null?null:GuestIdentityVaultDao.configured().readIdentity(c,id));
                row.put("loyalty_level",r.getString("loyalty_level"));row.put("created_at",r.getString("created_at"));rows.add(row);
                row.put("address",r.getString("address"));row.put("date_of_birth",r.getString("date_of_birth"));
            }}return rows;
        }catch(SQLException e){throw new IllegalStateException("Customer search unavailable",e);}
    }
}
