package biz.ugur.busroutebackend.business.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateBusinessCommand(
        @JsonProperty("name")                 String name,
        @JsonProperty("business_type")        String businessType,
        @JsonProperty("tax_number")           String taxNumber,
        @JsonProperty("registration_number")  String registrationNumber,
        @JsonProperty("contact_person")       String contactPerson,
        @JsonProperty("contact_phone")        String contactPhone,
        @JsonProperty("contact_email")        String contactEmail,
        @JsonProperty("website_url")          String websiteUrl,
        @JsonProperty("country")              String country,
        @JsonProperty("city")                 String city,
        @JsonProperty("address_line")         String addressLine,
        @JsonProperty("logo_url")             String logoUrl,
        @JsonProperty("description")          String description
) {}
