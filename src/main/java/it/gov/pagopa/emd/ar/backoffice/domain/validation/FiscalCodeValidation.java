package it.gov.pagopa.emd.ar.backoffice.domain.validation;

/** Fiscal-code format shared by BFF request constraints, aligned with Citizen's canonical validator. */
public final class FiscalCodeValidation {

    public static final String COMPLETE_FISCAL_CODE_REGEX =
            "^[A-Za-z]{6}[0-9]{2}[A-Za-z][0-9]{2}[A-Za-z][0-9]{3}[A-Za-z]$";

    private FiscalCodeValidation() { }
}
