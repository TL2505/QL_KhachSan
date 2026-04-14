package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.ServiceUsage;
import java.util.ArrayList;

public interface ServiceUsageDAO {
    void addServiceUsage(ServiceUsage usage);
    void deleteServiceUsage(int id);
    ArrayList<ServiceUsage> getUsageByBookingId(int bookingId);
}
