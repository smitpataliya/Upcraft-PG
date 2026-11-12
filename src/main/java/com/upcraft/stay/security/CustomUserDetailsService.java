package com.upcraft.stay.security;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.repository.UserRepository;

/**
 * Loads active platform user credentials and authorities from MySQL.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String cleanIdentifier = StringUtils.trimToEmpty(identifier).toLowerCase();
        User userEntity = userRepository.findByEmailOrUsername(cleanIdentifier, cleanIdentifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
                userEntity.getEmail(),
                StringUtils.defaultString(userEntity.getPassword()),
                userEntity.isActive(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority(AppConstants.ROLE_PREFIX + userEntity.getRole().name()))
        );
    }
}
