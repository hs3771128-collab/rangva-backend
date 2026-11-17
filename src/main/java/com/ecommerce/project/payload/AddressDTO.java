package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    private Long addressId;
    private String street;
    private String buildingName;
    private String area;
    private String city;
    /** Province: Punjab, Sindh, KPK, Balochistan, ICT, GB or AJK. */
    private String state;
    private String country;
    private String pincode;
    private String phone;

    public String getFormatted() {
        StringBuilder sb = new StringBuilder();
        if (buildingName != null && !buildingName.isBlank()) sb.append(buildingName).append(", ");
        if (street != null) sb.append(street);
        if (area != null && !area.isBlank()) sb.append(", ").append(area);
        if (city != null) sb.append(", ").append(city);
        if (state != null) sb.append(", ").append(state);
        return sb.toString();
    }
}
