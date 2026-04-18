package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Service;
import java.util.ArrayList;

public interface ServiceDAO {
    void addService(Service service);
    void updateService(Service service);
    void deleteService(int id);
    ArrayList<Service> selectAllServices();
    Service findById(int id);
}
