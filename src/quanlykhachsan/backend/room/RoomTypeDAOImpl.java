package quanlykhachsan.backend.room;

import quanlykhachsan.backend.utils.DBconn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RoomTypeDAOImpl implements RoomTypeDAO {

    @Override
    public List<RoomType> findAll() {
        List<RoomType> list = new ArrayList<>();
        String sql = "SELECT t.*,COUNT(r.id) AS room_count FROM room_types t LEFT JOIN rooms r ON r.room_type_id=t.id GROUP BY t.id ORDER BY t.display_order,t.name";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RoomType rt = new RoomType();
                rt.setId(rs.getInt("id"));
                rt.setName(rs.getString("name"));
                rt.setDescription(rs.getString("description"));
                rt.setBasePrice(rs.getDouble("base_price"));
                rt.setCapacity(rs.getInt("capacity"));
                rt.setImageUrl(rs.getString("image_url")); rt.setImageAlt(rs.getString("image_alt"));
                rt.setAmenities(rs.getString("amenities")); rt.setVisible(rs.getBoolean("is_visible"));
                rt.setRoomCount(rs.getInt("room_count"));
                list.add(rt);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tải hạng phòng từ cơ sở dữ liệu.", e);
        }
        return list;
    }

    @Override
    public RoomType findById(int id) {
        String sql = "SELECT t.*,(SELECT COUNT(*) FROM rooms r WHERE r.room_type_id=t.id) AS room_count FROM room_types t WHERE t.id = ?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RoomType rt = new RoomType();
                    rt.setId(rs.getInt("id"));
                    rt.setName(rs.getString("name"));
                    rt.setDescription(rs.getString("description"));
                    rt.setBasePrice(rs.getDouble("base_price"));
                    rt.setCapacity(rs.getInt("capacity"));
                    rt.setImageUrl(rs.getString("image_url")); rt.setImageAlt(rs.getString("image_alt"));
                    rt.setAmenities(rs.getString("amenities")); rt.setVisible(rs.getBoolean("is_visible"));
                    rt.setRoomCount(rs.getInt("room_count"));
                    return rt;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tải hạng phòng từ cơ sở dữ liệu.", e);
        }
        return null;
    }

    @Override public boolean create(RoomType type) {
        String sql = "INSERT INTO room_types(name,description,base_price,capacity,image_url,image_alt,amenities,is_visible) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection c=DBconn.getConnection(); PreparedStatement q=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bind(q,type); q.executeUpdate();
            try(ResultSet rs=q.getGeneratedKeys()){if(rs.next())type.setId(rs.getInt(1));}
            return type.getId()>0;
        } catch(java.sql.SQLIntegrityConstraintViolationException ex) { throw new RoomTypeConflictException("Tên hạng phòng đã tồn tại.",ex); }
        catch(Exception ex) { throw new IllegalStateException("Không thể thêm hạng phòng lúc này.",ex); }
    }

    @Override public boolean update(RoomType type) {
        String sql = "UPDATE room_types SET name=?,description=?,base_price=?,capacity=?,image_url=?,image_alt=?,amenities=?,is_visible=? WHERE id=?";
        try(Connection c=DBconn.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
            bind(q,type);q.setInt(9,type.getId());return q.executeUpdate()==1;
        } catch(java.sql.SQLIntegrityConstraintViolationException ex) { throw new RoomTypeConflictException("Tên hạng phòng đã tồn tại.",ex); }
        catch(Exception ex) { throw new IllegalStateException("Không thể cập nhật hạng phòng lúc này.",ex); }
    }

    @Override public boolean delete(int id) {
        try(Connection c=DBconn.getConnection()) {
            try(PreparedStatement check=c.prepareStatement("SELECT COUNT(*) FROM rooms WHERE room_type_id=?")) {
                check.setInt(1,id);try(ResultSet rs=check.executeQuery()){if(rs.next()&&rs.getInt(1)>0)throw new RoomTypeConflictException("Hạng phòng đang được gán cho phòng vật lý. Hãy chuyển hoặc xóa các phòng đó trước.");}
            }
            try(PreparedStatement q=c.prepareStatement("DELETE FROM room_types WHERE id=?")){q.setInt(1,id);return q.executeUpdate()==1;}
        } catch(RoomTypeConflictException ex) { throw ex; }
        catch(java.sql.SQLIntegrityConstraintViolationException ex) { throw new RoomTypeConflictException("Không thể xóa hạng phòng đang được dữ liệu khác sử dụng.",ex); }
        catch(Exception ex) { throw new IllegalStateException("Không thể xóa hạng phòng lúc này.",ex); }
    }

    private static void bind(PreparedStatement q,RoomType type)throws java.sql.SQLException {
        q.setString(1,type.getName());q.setString(2,type.getDescription());q.setDouble(3,type.getBasePrice());q.setInt(4,type.getCapacity());
        q.setString(5,type.getImageUrl());q.setString(6,type.getImageAlt());q.setString(7,type.getAmenities());q.setBoolean(8,type.isVisible());
    }
}
