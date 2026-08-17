package tn.rihab.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tn.rihab.authservice.DTO.LoginRequest;
import tn.rihab.authservice.DTO.LoginResponse;
import tn.rihab.authservice.entity.CredentialAccount;
import tn.rihab.authservice.exception.AuthException;
import tn.rihab.authservice.repository.CredentialAccountRepository;
import tn.rihab.authservice.repository.RefreshTokenRepository;
import tn.rihab.authservice.security.JwtService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class) // <-- FORCE L'ORDRE ALPHABETIQUE (1, 2, 3, 4, 5)
class AuthServiceTest {

    @Mock
    private CredentialAccountRepository accountRepo;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshRepo;
    @Mock
    private AuditService auditService;
    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AuthService authService;

    private CredentialAccount validAccount;

    @BeforeEach
    void setUp() {
        validAccount = new CredentialAccount();
        validAccount.setEmail("manager@projectiq.tn");
        validAccount.setPasswordHash("encoded_password");
        validAccount.setAccountStatus("ACTIVE");
        validAccount.setRole("MANAGER");
        validAccount.setFailedLoginCount(0); // <-- Fix: JPA @PrePersist n'est pas appelé dans les tests Mockito
    }

    // --- CAS SENSIBLE 1 : Tentative de connexion avec un compte en attente ---
    @Test
    void TF_01_ConnexionRefusee_CompteEnAttente() {
        validAccount.setAccountStatus("PENDING");
        LoginRequest request = new LoginRequest();
        request.setEmail("manager@projectiq.tn");
        request.setPassword("password123");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(validAccount));

        AuthException exception = assertThrows(AuthException.class, () -> authService.login(request, null));
        assertEquals("Votre compte est en attente de validation.", exception.getMessage());
        
        verify(auditService).saveAudit(any(), eq(validAccount.getEmail()), eq("LOGIN_BLOCKED"), eq("FAILURE"), any(), any());
    }

    // --- CAS SENSIBLE 2 : Tentative de connexion avec un compte Banni/Rejeté ---
    @Test
    void TF_02_ConnexionRefusee_CompteRejete() {
        validAccount.setAccountStatus("BANNED");
        LoginRequest request = new LoginRequest();
        request.setEmail("manager@projectiq.tn");
        request.setPassword("password123");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(validAccount));

        AuthException exception = assertThrows(AuthException.class, () -> authService.login(request, null));
        assertEquals("Ce compte a été refusé ou désactivé.", exception.getMessage());
    }

    // --- CAS SENSIBLE 3 : Connexion Réussie (Génération JWT) ---
    @Test
    void TF_03_ConnexionValide_GenerationDesJetons() {
        LoginRequest request = new LoginRequest();
        request.setEmail("manager@projectiq.tn");
        request.setPassword("password123");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(validAccount));
        when(encoder.matches(request.getPassword(), validAccount.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(validAccount.getEmail(), validAccount.getRole())).thenReturn("mocked-jwt-token");

        LoginResponse response = authService.login(request, null);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.getAccessToken());
        assertEquals("MANAGER", response.getRole());
        verify(auditService).saveAudit(any(), eq(validAccount.getEmail()), eq("LOGIN_SUCCESS"), eq("SUCCESS"), any(), any());
    }

    // --- CAS SENSIBLE 4 : Mauvais mot de passe ---
    @Test
    void TF_04_ConnexionInvalide_MauvaisMotDePasse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("manager@projectiq.tn");
        request.setPassword("wrong_password");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(validAccount));
        when(encoder.matches(request.getPassword(), validAccount.getPasswordHash())).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> authService.login(request, null));
        assertEquals("Identifiants incorrects", exception.getMessage());
        
        // Vérifie qu'on a bien incrémenté les échecs et sauvegardé le compte
        assertEquals(1, validAccount.getFailedLoginCount());
        verify(accountRepo).save(validAccount);
        verify(auditService).saveAudit(any(), eq(validAccount.getEmail()), eq("LOGIN_FAILURE"), eq("FAILURE"), any(), any());
    }

    // --- CAS SENSIBLE 5 : Verrouillage après 5 essais ---
    @Test
    void TF_05_VerrouillageApresCinqEssais() {
        // On simule un compte qui est déjà à 4 échecs
        validAccount.setFailedLoginCount(4);
        LoginRequest request = new LoginRequest();
        request.setEmail("manager@projectiq.tn");
        request.setPassword("wrong_password");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(validAccount));
        when(encoder.matches(request.getPassword(), validAccount.getPasswordHash())).thenReturn(false);

        // 5ème tentative échouée
        assertThrows(AuthException.class, () -> authService.login(request, null));
        
        // Le statut doit passer à LOCKED
        assertEquals("LOCKED", validAccount.getAccountStatus());
        assertEquals(5, validAccount.getFailedLoginCount());
        verify(accountRepo).save(validAccount);

        // Si on tente de se reconnecter, on doit être bloqué avec le message de verrouillage
        LoginRequest requestVerrouille = new LoginRequest();
        requestVerrouille.setEmail("manager@projectiq.tn");
        requestVerrouille.setPassword("password123"); // Même avec le bon mot de passe, c'est bloqué
        
        AuthException exception = assertThrows(AuthException.class, () -> authService.login(requestVerrouille, null));
        assertEquals("Ce compte est verrouillé suite à de trop nombreuses tentatives échouées.", exception.getMessage());
    }

    // --- TEST NON-FONCTIONNEL : TNF-01 - Hachage BCrypt ---
    @Test
    void TNF_01_BCrypt_Password_Hash() {
        tn.rihab.authservice.DTO.RegisterRequest request = new tn.rihab.authservice.DTO.RegisterRequest();
        request.setEmail("nouveau@projectiq.tn");
        request.setPassword("SuperSecret123!");
        request.setFullName("Nouveau Utilisateur");

        when(accountRepo.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(encoder.encode(request.getPassword())).thenReturn("$2a$10$FakeBcryptHashExampleString...");

        // On appelle l'inscription
        String resultat = authService.register(request);

        // Vérifications
        assertTrue(resultat.contains("Inscription réussie"));
        verify(encoder, times(1)).encode("SuperSecret123!");
        
        // On capture l'objet sauvegardé pour vérifier qu'il contient bien le hash et non le mot de passe en clair
        org.mockito.ArgumentCaptor<CredentialAccount> accountCaptor = org.mockito.ArgumentCaptor.forClass(CredentialAccount.class);
        verify(accountRepo).save(accountCaptor.capture());
        
        CredentialAccount savedAccount = accountCaptor.getValue();
        assertEquals("$2a$10$FakeBcryptHashExampleString...", savedAccount.getPasswordHash(), "Le mot de passe en base doit être l'empreinte BCrypt");
        assertNotEquals("SuperSecret123!", savedAccount.getPasswordHash(), "Le mot de passe ne doit JAMAIS être stocké en clair");
    }
}
