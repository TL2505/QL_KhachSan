package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.RoomTypeDAO;
import quanlykhachsan.backend.model.RoomType;
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
        String sql = "SELECT * FROM room_types";
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
                list.add(rt);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public RoomType findById(int id) {
        String sql = "SELECT * FROM room_types WHERE id = ?";
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
                    return rt;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
