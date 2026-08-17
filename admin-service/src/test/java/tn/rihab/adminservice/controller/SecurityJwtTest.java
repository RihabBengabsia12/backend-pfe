package tn.rihab.adminservice.controller;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import tn.rihab.adminservice.config.JwtUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import tn.rihab.adminservice.config.SecurityConfig;
import tn.rihab.adminservice.config.JwtAuthenticationFilter;
import tn.rihab.adminservice.service.UserService;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestMethodOrder(MethodOrderer.MethodName.class)
class SecurityJwtTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserService userService;

    // --- TEST NON-FONCTIONNEL : TNF-02 - Sécurité JWT ---

    @Test
    void TNF_02_01_Rejet_Requete_Sans_Token() throws Exception {
        // Tentative d'accès à une route protégée sans Header Authorization
        mockMvc.perform(get("/api/admin/users"))
                // Spring Security renvoie 403 Forbidden ou 401 Unauthorized quand un accès anonyme tente d'accéder à hasAuthority
                .andExpect(status().is4xxClientError());
    }

    @Test
    void TNF_02_02_Rejet_Requete_Token_Invalide() throws Exception {
        when(jwtUtils.extractUsername(anyString())).thenReturn("fake@projectiq.tn");
        when(jwtUtils.isTokenValid(anyString())).thenReturn(false);

        // Tentative d'accès avec un faux token
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer fake-invalid-token-12345"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void TNF_02_03_Rejet_Requete_Mauvais_Role() throws Exception {
        when(jwtUtils.extractUsername(anyString())).thenReturn("guest@projectiq.tn");
        when(jwtUtils.isTokenValid(anyString())).thenReturn(true);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("GUEST")))
                .when(jwtUtils).extractAuthorities(anyString());

        String guestToken = "token.guest.valide";

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + guestToken))
                // On a un token valide (donc on est authentifié), mais on n'a pas le droit (hasAuthority("ADMIN"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void TNF_02_04_Acceptation_Requete_Role_Admin() throws Exception {
        when(jwtUtils.extractUsername(anyString())).thenReturn("admin@projectiq.tn");
        when(jwtUtils.isTokenValid(anyString())).thenReturn(true);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ADMIN")))
                .when(jwtUtils).extractAuthorities(anyString());

        org.mockito.Mockito.when(userService.findAll(0, 20)).thenReturn(org.springframework.data.domain.Page.empty());

        String adminToken = "token.admin.valide";

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                // Doit passer la sécurité (200 OK)
                .andExpect(status().isOk());
    }
}
