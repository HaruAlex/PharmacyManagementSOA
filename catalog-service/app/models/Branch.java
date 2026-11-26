package models;

import java.time.LocalDate;

/**
 * Branch data exposed by the Catalog API.
 *
 * <p>The API uses STORE/WAREHOUSE while the shared database stores
 * PHARMACY/CENTRAL_WAREHOUSE; the repository performs that translation.</p>
 */
public class Branch {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String type;
    private String status;
    private String licenseNumber;
    private LocalDate licenseExpiry;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
}
