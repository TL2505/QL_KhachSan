package quanlykhachsan.backend.report;

import java.util.Date;

public class DashboardFilter {
    private Date fromDate;
    private Date toDate;
    private Integer roomTypeId;

    public DashboardFilter() {
    }

    public DashboardFilter(Date fromDate, Date toDate, Integer roomTypeId) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.roomTypeId = roomTypeId;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }

    public Integer getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId(Integer roomTypeId) {
        this.roomTypeId = roomTypeId;
    }
}
