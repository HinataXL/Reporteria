package com.erick.soporte.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_clients")
public class SupportClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zoho_contact_id", unique = true)
    private String zohoContactId;

    @Column(name = "zoho_account_id")
    private String zohoAccountId;

    private String firstName;
    private String lastName;
    private String fullName;
    private String accountName;
    private String email;
    private String phone;
    private String mobile;
    private Boolean active = true;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime zohoCreatedTime;
    private LocalDateTime zohoModifiedTime;

    public Long getId() {
        return id;
    }

    public String getZohoContactId() {
        return zohoContactId;
    }

    public void setZohoContactId(String zohoContactId) {
        this.zohoContactId = zohoContactId;
    }

    public String getZohoAccountId() {
        return zohoAccountId;
    }

    public void setZohoAccountId(String zohoAccountId) {
        this.zohoAccountId = zohoAccountId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public LocalDateTime getZohoCreatedTime() {
        return zohoCreatedTime;
    }

    public void setZohoCreatedTime(LocalDateTime zohoCreatedTime) {
        this.zohoCreatedTime = zohoCreatedTime;
    }

    public LocalDateTime getZohoModifiedTime() {
        return zohoModifiedTime;
    }

    public void setZohoModifiedTime(LocalDateTime zohoModifiedTime) {
        this.zohoModifiedTime = zohoModifiedTime;
    }
}
