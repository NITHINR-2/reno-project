package com.renault.fleet.user.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.user.dto.RoleDto;
import com.renault.fleet.user.dto.RoleDto.PermissionDto;
import com.renault.fleet.user.entity.Permission;
import com.renault.fleet.user.entity.Role;
import com.renault.fleet.user.repository.RoleRepository;
import java.util.Comparator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleQueryService {

    private final RoleRepository roles;

    public RoleQueryService(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleDto> list(Pageable pageable) {
        return PageResponse.from(roles.findAll(pageable), RoleQueryService::toDto);
    }

    private static RoleDto toDto(Role role) {
        return new RoleDto(role.getCode(), role.getName(), role.getDescription(), role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(p -> new PermissionDto(p.getCode(), p.getDescription()))
                .toList());
    }
}
