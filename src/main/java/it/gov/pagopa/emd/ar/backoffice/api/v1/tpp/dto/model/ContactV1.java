package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a contact entity containing basic contact information.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactV1 {
    private String name;

    @Pattern(regexp = "^\\d{9,10}$", message = "Number must be between 9 and 10 digits")
    private String number;

    @Email(message = "Email must be valid")
    private String email;
}

