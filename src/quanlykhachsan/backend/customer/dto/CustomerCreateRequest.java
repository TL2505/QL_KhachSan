package quanlykhachsan.backend.customer.dto;

public class CustomerCreateRequest {
    private String name;
    private String phone;
    private String cccd;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCccd() { return cccd; }
    public void setCccd(String cccd) { this.cccd = cccd; }
}
