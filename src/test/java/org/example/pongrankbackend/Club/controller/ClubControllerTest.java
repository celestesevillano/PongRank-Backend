package org.example.pongrankbackend.Club.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.dto.*;
import org.example.pongrankbackend.Club.service.ClubService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.common.exception.GlobalExceptionHandler;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ClubController.class)
@Import({GlobalExceptionHandler.class, ClubControllerTest.SecurityTestConfig.class})
class ClubControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private ClubService clubService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails authenticatedUser;

    @BeforeEach
    void setUp() {
        Player player = Player.builder()
                .id(1L)
                .email("admin@pongrank.com")
                .password("password")
                .role(Role.ROLE_CLUB_ADMIN)
                .status(PlayerStatus.ACTIVE)
                .build();
        authenticatedUser = new CustomUserDetails(player);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                authenticatedUser, null, authenticatedUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // 1. Registrar Club (POST /api/v1/clubs)
    // ==========================================

    @Test
    @DisplayName("201 Created - Registro de club exitoso")
    void registerClub_Success_Returns201() throws Exception {
        ClubRegisterRequestDTO request = ClubRegisterRequestDTO.builder()
                .name("Club Tenis de Mesa Lima")
                .address("Av. Salaverry 1234")
                .affiliationDocumentUrl("https://storage.pongrank.com/docs/lima.pdf")
                .build();

        ClubResponseDTO response = ClubResponseDTO.builder()
                .id(10L)
                .name("Club Tenis de Mesa Lima")
                .address("Av. Salaverry 1234")
                .status(ClubStatus.PENDING)
                .admin(PlayerSummaryDTO.builder().id(1L).name("Admin Club").build())
                .build();

        given(clubService.registerClub(any(ClubRegisterRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Club Tenis de Mesa Lima")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.admin.id", is(1)));
    }

    @Test
    @DisplayName("400 Bad Request - Validación Jakarta falla si faltan campos obligatorios")
    void registerClub_ValidationError_Returns400() throws Exception {
        ClubRegisterRequestDTO invalidRequest = ClubRegisterRequestDTO.builder()
                .name("")
                .address("")
                .build();

        mockMvc.perform(post("/api/v1/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("nombre")));
    }

    @Test
    @DisplayName("401/403 - Registro no autenticado es rechazado")
    void registerClub_Unauthenticated_ReturnsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        ClubRegisterRequestDTO request = ClubRegisterRequestDTO.builder()
                .name("Club Tenis de Mesa Lima")
                .address("Av. Salaverry 1234")
                .affiliationDocumentUrl("https://storage.pongrank.com/docs/lima.pdf")
                .build();

        mockMvc.perform(post("/api/v1/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // ==========================================
    // 2. Obtener Clubes Aprobados (GET /api/v1/clubs)
    // ==========================================

    @Test
    @DisplayName("200 OK - Listado paginado de clubes aprobados")
    void getApprovedClubs_Success_ReturnsPage() throws Exception {
        ClubResponseDTO club = ClubResponseDTO.builder()
                .id(10L)
                .name("Club Aprobado")
                .address("Calle 1")
                .status(ClubStatus.APPROVED)
                .build();

        PageResponseDTO<ClubResponseDTO> pageResponse = PageResponseDTO.<ClubResponseDTO>builder()
                .content(List.of(club))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        given(clubService.getApprovedClubs(0, 10)).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/clubs?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(10)))
                .andExpect(jsonPath("$.content[0].name", is("Club Aprobado")));
    }

    // ==========================================
    // 3. Obtener Club por ID (GET /api/v1/clubs/{id})
    // ==========================================

    @Test
    @DisplayName("200 OK - Obtener club existente por id")
    void getClubById_Success_Returns200() throws Exception {
        ClubResponseDTO club = ClubResponseDTO.builder()
                .id(10L)
                .name("Club Miraflores")
                .address("Av. Larco 456")
                .status(ClubStatus.APPROVED)
                .build();

        given(clubService.getClubById(10L)).willReturn(club);

        mockMvc.perform(get("/api/v1/clubs/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Club Miraflores")));
    }

    @Test
    @DisplayName("404 Not Found - Club inexistente lanza ResourceNotFoundException")
    void getClubById_NotFound_Returns404() throws Exception {
        given(clubService.getClubById(999L))
                .willThrow(new ResourceNotFoundException("Club no encontrado con id: 999"));

        mockMvc.perform(get("/api/v1/clubs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Club no encontrado")));
    }

    // ==========================================
    // 4. Actualizar Club (PATCH /api/v1/clubs/{id})
    // ==========================================

    @Test
    @DisplayName("200 OK - Actualizar datos del club")
    void updateClub_Success_Returns200() throws Exception {
        ClubUpdateRequestDTO request = ClubUpdateRequestDTO.builder()
                .name("Club Actualizado")
                .address("Nueva Direccion 789")
                .build();

        ClubResponseDTO response = ClubResponseDTO.builder()
                .id(10L)
                .name("Club Actualizado")
                .address("Nueva Direccion 789")
                .status(ClubStatus.APPROVED)
                .build();

        given(clubService.updateClub(eq(10L), any(ClubUpdateRequestDTO.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/clubs/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Club Actualizado")))
                .andExpect(jsonPath("$.address", is("Nueva Direccion 789")));
    }

    // ==========================================
    // 5. Transferir Administración (PATCH /api/v1/clubs/{id}/admin)
    // ==========================================

    @Test
    @DisplayName("200 OK - Transferencia de administración exitosa")
    void transferAdministration_Success_Returns200() throws Exception {
        ClubAdminTransferRequestDTO request = ClubAdminTransferRequestDTO.builder()
                .newAdminPlayerId(2L)
                .build();

        ClubResponseDTO response = ClubResponseDTO.builder()
                .id(10L)
                .name("Club Miraflores")
                .admin(PlayerSummaryDTO.builder().id(2L).name("Nuevo Admin").build())
                .status(ClubStatus.APPROVED)
                .build();

        given(clubService.transferAdministration(eq(10L), any(ClubAdminTransferRequestDTO.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/clubs/10/admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.admin.id", is(2)));
    }

    @Test
    @DisplayName("400 Bad Request - Transferencia sin nuevo administrador")
    void transferAdministration_MissingAdmin_Returns400() throws Exception {
        ClubAdminTransferRequestDTO invalidRequest = ClubAdminTransferRequestDTO.builder()
                .newAdminPlayerId(null)
                .build();

        mockMvc.perform(patch("/api/v1/clubs/10/admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("nuevo administrador")));
    }
}
