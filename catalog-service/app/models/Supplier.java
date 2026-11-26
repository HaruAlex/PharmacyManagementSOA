package models;

/** Supplier fields present in the shared catalog schema. */
public class Supplier {
    private Long id;
    private String code;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String taxCode;
    private String status;
    private String contactPerson;
    private String contactPhone;
    private int paymentTermDays;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public int getPaymentTermDays() { return paymentTermDays; }
    public void setPaymentTermDays(int paymentTermDays) { this.paymentTermDays = paymentTermDays; }
}
