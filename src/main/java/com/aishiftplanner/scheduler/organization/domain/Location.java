package com.aishiftplanner.scheduler.organization.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.ZoneId;
import java.util.UUID;

/**
 * A physical site (e.g. "Mainz Innenstadt").
 *
 * <p>Each location owns its own IANA timezone. Every scheduling calculation for shifts at
 * this location is done in that zone, which is why shift times are stored as local
 * date/time rather than instants: a shift that starts at 18:00 starts at 18:00 on both
 * sides of a DST transition, and the day it belongs to is unambiguous even when a calendar
 * day is 23 or 25 hours long.
 */
@Entity
@Table(name = "locations")
public class Location extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Europe/Berlin";

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Location() {
        // for JPA
    }

    public Location(UUID organizationId, String name, String timezone) {
        setOrganizationId(organizationId);
        this.name = name;
        this.timezone = timezone;
    }

    /**
     * @return the location's timezone as a {@link ZoneId}; throws if the stored value is not
     *     a valid IANA zone, which is preferable to silently scheduling in the wrong zone.
     */
    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
