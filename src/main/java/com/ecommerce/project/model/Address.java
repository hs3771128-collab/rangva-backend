package com.ecommerce.project.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    /** House / flat number and street, e.g. "House 12, Street 5". */
    @NotBlank
    @Size(min = 5, message = "Street name must be atleast 5 characters")
    private String street;

    /** Building or house name, e.g. "Al-Noor Apartments". Optional. */
    private String buildingName;

    /** Area / neighbourhood, e.g. "DHA Phase 5, Block C". */
    private String area;

    @NotBlank
    @Size(min = 3, message = "City name must be atleast 3 characters")
    private String city;

    /** Province: Punjab, Sindh, KPK, Balochistan, ICT, GB or AJK. */
    @NotBlank
    @Size(min = 2, message = "Province must be atleast 2 characters")
    private String state;

    /** Always Pakistan for a domestic order. */
    private String country = "Pakistan";

    /** Postal code, optional. */
    private String pincode;

    /** Mobile number used by the courier: +92 3XX XXXXXXX */
    @NotBlank
    @Size(min = 7, message = "Phone number is required")
    private String phone;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    public Address(String street, String buildingName, String city, String state, String country,
                   String pincode, String phone, String area) {
        this.street = street;
        this.buildingName = buildingName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pincode = pincode;
        this.phone = phone;
        this.area = area;
    }
}
