package quanlykhachsan.backend.enums;

import com.google.gson.annotations.SerializedName;

public enum BookingStatus {
    @SerializedName("pending")
    PENDING,
    @SerializedName("confirmed")
    CONFIRMED,
    @SerializedName("checked_in")
    CHECKED_IN,
    @SerializedName("checked_out")
    CHECKED_OUT,
    @SerializedName("cancelled")
    CANCELLED;

    public static BookingStatus fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Trạng thái phòng không được để trống!");
        }
        for (BookingStatus status : BookingStatus.values()) {
            if (status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Trạng thái phòng không hợp lệ từ Database: " + text);
    }
}
