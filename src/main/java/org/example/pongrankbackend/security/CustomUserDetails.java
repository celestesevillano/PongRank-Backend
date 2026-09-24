package org.example.pongrankbackend.security;

import lombok.Getter;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String role;
    private final PlayerStatus status;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Player player) {
        this.id = player.getId();
        this.email = player.getEmail();
        this.password = player.getPassword();
        this.role = player.getRole().name();
        this.status = player.getStatus();
        this.authorities = List.of(new SimpleGrantedAuthority(player.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != PlayerStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == PlayerStatus.ACTIVE;
    }
}
