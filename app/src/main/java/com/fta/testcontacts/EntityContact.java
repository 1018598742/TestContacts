package com.fta.testcontacts;

public class EntityContact {

    private String name;

    private String mobile_num;

    private String office_num;

    private String home_num;

    private String email;

    private String home_address;

    private String office_address;

    private String extend;

    private String photo;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMobile_num() {
        return mobile_num;
    }

    public void setMobile_num(String mobile_num) {
        this.mobile_num = mobile_num;
    }

    public String getOffice_num() {
        return office_num;
    }

    public void setOffice_num(String office_num) {
        this.office_num = office_num;
    }

    public String getHome_num() {
        return home_num;
    }

    public void setHome_num(String home_num) {
        this.home_num = home_num;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHome_address() {
        return home_address;
    }

    public void setHome_address(String home_address) {
        this.home_address = home_address;
    }

    public String getOffice_address() {
        return office_address;
    }

    public void setOffice_address(String office_address) {
        this.office_address = office_address;
    }

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    @Override
    public String toString() {
        return "EntityContact{" +
                "name='" + name + '\'' +
                ", mobile_num='" + mobile_num + '\'' +
                ", office_num='" + office_num + '\'' +
                ", home_num='" + home_num + '\'' +
                ", email='" + email + '\'' +
                ", home_address='" + home_address + '\'' +
                ", office_address='" + office_address + '\'' +
                ", extend='" + extend + '\'' +
                ", photo='" + photo + '\'' +
                '}';
    }
}
