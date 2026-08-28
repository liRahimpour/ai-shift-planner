package com.aishiftplanner.scheduler.organization.application;

import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.CreateLocationRequest;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.LocationResponse;
import com.aishiftplanner.scheduler.organization.api.OrganizationDtos.UpdateLocationRequest;
import com.aishiftplanner.scheduler.organization.domain.Location;
import com.aishiftplanner.scheduler.organization.infrastructure.LocationRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;

    public LocationService(LocationRepository locationRepository, CurrentUserProvider currentUser) {
        this.locationRepository = locationRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list() {
        return locationRepository
                .findAllByOrganizationIdOrderByNameAsc(currentUser.requireOrganizationId())
                .stream()
                .map(LocationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse get(UUID id) {
        return toResponse(loadInTenant(id));
    }

    @Transactional
    public LocationResponse create(CreateLocationRequest request) {
        UUID organizationId = currentUser.requireOrganizationId();
        validateTimezone(request.timezone());

        if (locationRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw ApiException.conflict(
                    ErrorCode.ALREADY_EXISTS, "A location with this name already exists.");
        }

        Location location = new Location(organizationId, request.name(), request.timezone());
        location.setAddressLine(request.addressLine());
        location.setPostalCode(request.postalCode());
        location.setCity(request.city());
        location.setCountryCode(request.countryCode());

        return toResponse(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest request) {
        validateTimezone(request.timezone());
        Location location = loadInTenant(id);

        location.setName(request.name());
        location.setTimezone(request.timezone());
        location.setAddressLine(request.addressLine());
        location.setPostalCode(request.postalCode());
        location.setCity(request.city());
        location.setCountryCode(request.countryCode());
        location.setActive(request.active());

        return toResponse(locationRepository.save(location));
    }

    /**
     * Loads a location and proves it belongs to the caller's organization in one query.
     *
     * <p>Reporting a foreign id as {@code 404} rather than {@code 403} is intentional: a
     * distinct "forbidden" answer would confirm that the id exists somewhere in the
     * platform, which is itself information a tenant should not have.
     */
    @Transactional(readOnly = true)
    public Location loadInTenant(UUID id) {
        return locationRepository
                .findByIdAndOrganizationId(id, currentUser.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Location not found."));
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            // Catching this here rather than at first use means an operator learns about the
            // typo when they save the location, not weeks later when a schedule silently
            // lands an hour off.
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED, "'" + timezone + "' is not a valid IANA timezone id.");
        }
    }

    static LocationResponse toResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getOrganizationId(),
                location.getName(),
                location.getTimezone(),
                location.getAddressLine(),
                location.getPostalCode(),
                location.getCity(),
                location.getCountryCode(),
                location.isActive());
    }
}
