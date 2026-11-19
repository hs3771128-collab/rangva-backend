package com.ecommerce.project.payload;

import java.util.HashSet;
import java.util.Set;

import com.ecommerce.project.model.Role;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// Here i am representing sellers as a user
public class UserDTO {

    private Long userId;
    private String username;
    private String email;
    // Never serialise the BCrypt hash back to the browser.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private Set<Role> roles = new HashSet<>();
    private AddressDTO address;
    private CartDTO cart;
}