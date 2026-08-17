# Chapitre 5 — Implémentation fonctionnelle

## 5.1 Introduction

Ce chapitre présente l’implémentation de ProjectIQ à partir des composants effectivement développés : une application web Angular, quatre microservices Spring Boot, une passerelle API, un serveur de découverte, un service IA FastAPI et les services d’infrastructure nécessaires à l’exécution locale. L’implémentation est organisée selon les cinq Epics du Product Backlog et les huit sprints définis au chapitre précédent.

La plateforme accompagne le traitement d’un dossier d’appel d’offres : dépôt du TDR, extraction d’informations, analyse approfondie, évaluation P-Win, matching avec le référentiel interne, génération de livrables, décision managériale et audit. Les traitements longs sont exécutés de façon asynchrone et leur progression est remontée vers le frontend par Server-Sent Events (SSE).

## 5.2 Environnement technique

| Couche | Technologies effectivement utilisées | Rôle |
|---|---|---|
| Frontend | Angular 17.0.5, TypeScript, PrimeNG 17.2.0, Chart.js | Interface web, routage, protection des routes, tableaux, formulaires et graphiques |
| Microservices métier | Java 21, Spring Boot 3.4.3 ; `analyste-service` en Spring Boot 3.2.5 | Authentification, administration, gestion des dossiers, analyse et production documentaire |
| Service IA | FastAPI 0.111.0, Pydantic 2.7.1, SDK Anthropic | Extraction, risques, matching et génération de contenus |
| Modèle IA | `claude-haiku-4-5-20251001` | Analyse et génération de texte |
| Persistance | PostgreSQL 16 Alpine | Stockage des données métier des services |
| Stockage objet | MinIO | TDR, textes extraits, documents DOCX, ZIP et rapports |
| Messagerie | RabbitMQ 3 Management Alpine | Événements asynchrones entre services |
| Découverte | Eureka | Enregistrement et découverte des microservices |
| Point d’entrée | Spring Cloud Gateway | Routage des requêtes du frontend vers les services |
| Messagerie de test | Mailpit | Réception et consultation locale des courriels |
| Conteneurisation | Docker Compose 3.8 | Démarrage de l’infrastructure et des services |

Le frontend utilise l’API Gateway sur `http://localhost:8089`. Le service IA est placé dans le dépôt frontend, dans le dossier `ia-service`, et est appelé par les services Spring via des clients Feign.

## 5.3 Module 1 — Sécurité, administration et référentiel (Epic 1, Sprints 1 et 2)

### 5.3.1 Objectif fonctionnel

Le premier module établit le socle de sécurité et d’administration de ProjectIQ. Il permet l’inscription, l’authentification, l’activation des comptes, la gestion des utilisateurs, des rôles, des permissions et des modules accessibles. Il comprend également l’audit des accès et des actions administratives.

| Sprint | Objectif | Story Points |
|---|---|---:|
| Sprint 1 | Socle de sécurité et gestion des comptes | 11 |
| Sprint 2 | Paramétrage métier, dictionnaires et audit | 14 |

### 5.3.2 Authentification et gestion des comptes

Le `auth-service` écoute sur le port 9090. Il persiste les comptes dans la table `credential_account`, les jetons de renouvellement dans `refresh_token` et les événements d’accès dans `access_event`.

Un compte comporte notamment un identifiant utilisateur, une adresse électronique unique, un mot de passe chiffré avec BCrypt, un état, un rôle, un compteur de tentatives échouées et des dates de création ou de dernière connexion. Les états utilisés sont `PENDING`, `VALIDATED`, `ACTIVE`, `REJECTED` et `LOCKED`. Après cinq tentatives de connexion infructueuses, le compte passe à l’état `LOCKED`.

L’inscription vérifie que l’adresse électronique n’est pas déjà utilisée, que le mot de passe contient au moins huit caractères et qu’il ne contient pas le nom ou le préfixe de l’adresse électronique. L’administrateur peut ensuite valider le dossier d’inscription, activer le compte et affecter son rôle. Le service initialise aussi un compte administrateur au démarrage lorsqu’il est absent.

Les jetons d’accès sont des JWT signés avec l’algorithme HS512. Ils contiennent l’adresse électronique comme sujet et le rôle de l’utilisateur. La durée configurée de l’Access Token est de 15 minutes. Le Refresh Token est généré sous forme d’UUID ; seule son empreinte SHA-256 est stockée en base. Sa durée de validité est de sept jours. Lors de la déconnexion, le jeton est révoqué.

Le filtre JWT protège les routes privées, extrait le jeton Bearer, le valide puis ajoute l’authentification dans le contexte Spring Security. Les routes de connexion, d’inscription, de renouvellement de jeton, de récupération de mot de passe, de réinitialisation et de santé restent accessibles sans authentification.

Les événements `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGIN_BLOCKED`, `LOGOUT`, `TOKEN_REFRESH`, `CRÉATION`, `VALIDATION_DOSSIER` et `ACTIVATION_FINALE` sont enregistrés. Chaque événement mémorise l’adresse utilisée, le résultat, l’adresse IP, l’agent utilisateur et la date de survenue.

### 5.3.3 Administration, rôles et Feature Flags

Le `admin-service` écoute sur le port 9091. Les tables `app_user`, `role`, `permission`, `user_role`, `role_permission`, `data_event` et `user_feature_flags` constituent son référentiel principal. Les migrations de cette base sont gérées avec Flyway.

Les rôles disponibles sont `ADMIN`, `ANALYST` et `MANAGER`. Les permissions sont regroupées par catégories fonctionnelles. Le service permet de modifier les permissions associées à un rôle et consigne ces modifications dans le journal `data_event`.

Les Feature Flags sont enregistrés par utilisateur et par code de module. Une contrainte d’unicité empêche de créer deux lignes pour le même couple `(user_email, module_code)`. Le frontend charge ces droits après connexion afin d’adapter les fonctionnalités visibles à l’utilisateur.

Le dictionnaire d’anonymisation n’est pas géré par `admin-service` : il appartient au `project-service`. Il contient le mot d’origine, le code de remplacement et son état d’activation. Cette séparation doit être respectée dans la description de l’architecture.

### 5.3.4 Interfaces à illustrer

- Figure 5.1 — Interface de connexion avec affichage des erreurs.
- Figure 5.2 — Gestion des comptes avec validation, activation ou rejet.
- Figure 5.3 — Gestion des rôles et des permissions.
- Figure 5.4 — Paramétrage des Feature Flags par utilisateur.
- Figure 5.5 — Journal des accès et des actions administratives.

Pour éviter une description trop technique, les routes suivantes peuvent être citées dans le texte ou présentées dans une annexe : `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`, `GET /api/admin/users`, `GET /api/admin/roles` et `GET /api/admin/audit/data`.

## 5.4 Module 2 — Dépôt, extraction et validation initiale (Epic 2, Sprints 3 et 4)

### 5.4.1 Objectif fonctionnel

Ce module prend en charge le dépôt d’un TDR, son stockage, l’extraction de son texte et l’analyse initiale par IA. Il comprend aussi la gestion des métadonnées d’extraction, la ré-extraction d’un champ et le paramétrage des prompts.

| Sprint | Objectif | Story Points |
|---|---|---:|
| Sprint 3 | Intégration IA, upload MinIO et extraction P1 | 14 |
| Sprint 4 | Analyse sémantique approfondie et Prompt Override | 13 |

### 5.4.2 Gestion du dossier et stockage

Le `project-service`, exposé sur le port 8083, gère l’entité `Dossier`. Un dossier contient les informations P1, des données internes, les valeurs calculées, l’état du workflow, l’auteur du dépôt, le caractère privé et les chemins des fichiers produits.

Lors du dépôt, le fichier TDR est enregistré dans le bucket MinIO des documents originaux. Son texte est ensuite extrait et sauvegardé dans un bucket de texte. La taille maximale configurée pour les fichiers téléversés est de 100 Mo.

Les douze informations prévues en phase 1 sont : pays, intitulé de l’offre, client, bailleurs, budget global, hommes-mois, date limite de soumission, langue, obligation et date de visite, obligation et date de conférence. Les métadonnées d’extraction conservent notamment la valeur proposée par l’IA, la valeur finale, le niveau de confiance, la source textuelle, l’origine de la valeur, la correction humaine et le nombre de ré-extractions.

Le calcul du TJM implicite est réalisé à partir du budget et du nombre d’hommes-mois lorsque ces données sont exploitables : `budget / (hommes-mois × 20)`. Le nombre de jours ouvrables restants est calculé à partir de la date limite. La priorité automatique vaut 1 pour dix jours ouvrables ou moins, 2 pour vingt jours ou moins, et 3 au-delà ; elle peut être remplacée manuellement.

### 5.4.3 Extraction P1, ré-extraction et indexation

Le lancement de l’analyse appelle l’endpoint IA de phase 1. Les résultats sont enregistrés dans les métadonnées, avec les alertes et les informations de coût retournées par le service IA. Le code actuel alimente directement l’entité `Dossier` avec les champs `PAYS`, `INTITULE_OFFRE`, `CLIENT`, `HOMMES_MOIS` et `DT_LIM_SOUM`. Les autres valeurs P1 restent consultables dans les métadonnées d’extraction.

Un champ peut être ré-extrait par l’endpoint `PUT /api/dossiers/{id}/reextract-field`. Deux ré-extractions au maximum sont autorisées par champ. La validation P1 permet d’enregistrer des corrections humaines et positionne le dossier à l’état `INDEXED`. Dans l’implémentation actuelle, les champs pays, budget et hommes-mois manquants déclenchent un avertissement dans les journaux, mais ne bloquent pas cette indexation.

Après indexation, le `project-service` publie l’événement RabbitMQ `DOSSIER_INDEXED`. Cet événement est consommé par l’`analyste-service` pour lancer le traitement approfondi.

### 5.4.4 Protection des dossiers privés

Pour un dossier privé, le service applique l’encapsulation DLP avant l’envoi du texte à l’IA. Les mots actifs du dictionnaire sont remplacés, sans remplacement partiel, au moyen d’expressions régulières utilisant les limites de mots. Les codes de remplacement ont le format `***CODE_xxxxxxxx***` et sont régénérés quotidiennement. Après le retour de l’IA, les valeurs et les sources peuvent être décapsulées avant leur affichage ou leur réutilisation.

### 5.4.5 Prompt Override et service IA

Le service IA expose la consultation et la mise à jour des prompts via `/config/prompts`. Le `project-service` propose aussi des overrides propres à un dossier. Le frontend comporte un composant de configuration permettant de sélectionner un prompt global ou d’enregistrer une adaptation liée à un dossier.

Le client Anthropic est créé une seule fois dans le service IA. Les appels utilisent le modèle Claude Haiku 4.5, avec au plus deux nouvelles tentatives après un échec et un délai progressif. Le système active le Prompt Caching éphémère sur le prompt système et sur le document. Les métriques retournées comprennent les tokens d’entrée, de sortie, de création ou de lecture de cache, le temps de traitement, le coût estimé et l’indicateur de cache.

### 5.4.6 Interfaces à illustrer

- Figure 5.6 — Dépôt d’un TDR avec date limite et indicateur de confidentialité.
- Figure 5.7 — Résultats d’extraction P1 : valeur, confiance et source.
- Figure 5.8 — Validation et correction des champs extraits.
- Figure 5.9 — Configuration ou override d’un prompt IA.

Les principales opérations exposées par ce module sont le dépôt (`POST /api/dossiers/upload`), le lancement de l’analyse (`POST /api/dossiers/{id}/analyze`), la consultation de l’extraction (`GET /api/dossiers/{id}/extraction-p1`), sa validation (`PUT /api/dossiers/{id}/validate-p1`) et la ré-extraction ciblée (`PUT /api/dossiers/{id}/reextract-field`).

## 5.5 Module 3 — Évaluation, matching et livrables (Epic 3, Sprints 5 et 6)

### 5.5.1 Objectif fonctionnel

Le `analyste-service` porte l’analyse approfondie des dossiers. Il réalise l’extraction P2, l’analyse des risques, le calcul P-Win, le matching avec le référentiel et la génération documentaire. Son port interne est 8080 ; Docker l’expose sur le port 8085.

| Sprint | Objectif | Story Points |
|---|---|---:|
| Sprint 5 | Scoring P-Win et matching | 13 |
| Sprint 6 | Génération APO, méthodologie et pack | 14 |

### 5.5.2 Pipeline asynchrone

L’événement `DOSSIER_INDEXED` déclenche `AnalyseDeepService` de manière asynchrone. Le service charge le dossier et le texte du document, réalise l’extraction P2 puis évalue les risques. Il diffuse au frontend les événements SSE `PIPELINE_START`, `DATA_LOADED`, `PHASE2_COMPLETED`, `RISKS_COMPLETED`, `PWIN_COMPLETED`, `MATCHING_COMPLETED`, `PIPELINE_PAUSED_FOR_VALIDATION`, `PIPELINE_STOPPED_NOGO` ou `PIPELINE_ERROR` selon l’état du traitement.

L’extraction P2 remplit les champs métier de l’analyse, notamment le mode de notation, la note minimale, la date limite des questions, le délai global, les informations de financement local, les données de caution, l’exigence de banque locale, les pondérations technique et financière ainsi que la date limite de soumission. Elle calcule aussi une indication sur la suffisance du délai de préparation.

Les dix risques analysés sont liés au pays et à la sécurité, aux finances, aux pénalités, aux exigences du TDR, aux garanties et assurances, à la taille et à la dispersion, aux frais divers, au budget et aux hommes-mois, à la participation locale et à la fiscalité. Chaque risque possède un niveau et une justification. Les résultats et les métriques d’appel IA sont conservés dans les journaux d’audit IA.

Si la décision automatique est `NO_GO` ou `MANUAL`, le service génère le rapport No-Go et arrête le pipeline. Dans le cas contraire, il poursuit avec le matching. Après le matching, le score P-Win est recalculé et le pipeline attend la validation manuelle avant la génération des livrables.

### 5.5.3 Score P-Win

Le score P-Win est calculé à partir de cinq axes : faisabilité, rentabilité, risques, concurrence et conformité. Les poids par défaut définis dans le chargeur de configuration sont respectivement 25 %, 25 %, 25 %, 15 % et 10 %. Les paramètres de scoring sont persistés et peuvent être gérés par l’interface d’administration.

La décision automatique et le motif principal sont enregistrés avec le score. Le modèle stocke également les scores par axe, l’existence éventuelle d’un risque rédhibitoire, les informations de Force-Go et leur justification. Les seuils utilisés doivent être décrits comme des paramètres de configuration et non comme des valeurs fixes, car le code contient des configurations de secours et des configurations chargées en base.

### 5.5.4 Matching avec le référentiel

Le moteur de matching compare les exigences extraites du dossier au référentiel de compétences, de références, d’experts et de relation client. Il calcule notamment les taux de couverture des compétences et des experts. L’IA peut extraire les exigences, produire une matrice de différenciation et proposer des correspondances d’experts. Les résultats sont stockés et disponibles dans l’interface de matching.

### 5.5.5 Génération des livrables

Après validation, le service peut générer les contenus APO, la méthodologie, la checklist, le rapport général et un pack ZIP. Les documents DOCX sont produits à partir de modèles présents dans les ressources du service et sont remplis à l’aide d’Apache POI. Les livrables sont déposés dans MinIO et leurs chemins sont associés au dossier.

Le rapport No-Go utilise un modèle DOCX et un contenu narratif demandé au service IA. Il reprend le score, les risques, le contexte du dossier et la décision proposée. Le pack regroupe les documents générés et les fichiers d’accompagnement disponibles au moment de sa création.

### 5.5.6 Interfaces à illustrer

- Figure 5.10 — Suivi du pipeline d’analyse en temps réel par SSE.
- Figure 5.11 — Score P-Win, détail par axe et risques identifiés.
- Figure 5.12 — Matching du référentiel et matrice de différenciation.
- Figure 5.13 — Rapport No-Go ou page de préparation du pack.
- Figure 5.14 — Journaux des appels IA : tokens, coût, temps et cache.

Les endpoints qui illustrent le mieux cette partie sont `POST /api/analyses/{id}/deep-analysis`, `GET /api/analyses/{id}/extraction-p2`, `GET /api/analyses/{id}/risks`, `GET /api/scoring/{id}/result`, `GET /api/matching/{id}/result` et `GET /api/analyses/stream/{dossierId}`.

## 5.6 Module 4 — Supervision et arbitrage managérial (Epic 4, Sprint 7)

### 5.6.1 Objectif fonctionnel

Le quatrième module fournit au manager une vue transverse des dossiers, des décisions et des validations. Il comprend le tableau de bord, la consultation de l’historique, l’arbitrage No-Go/Force-Go, les validations hiérarchiques et les accès aux rapports produits.

| Sprint | Objectif | Story Points |
|---|---|---:|
| Sprint 7 | Dashboard manager, arbitrage et historiques | 15 |

### 5.6.2 Tableau de bord et décisions

Le composant `ManagerDashboardComponent` rafraîchit ses données toutes les trente secondes. Il affiche le total des dossiers, les dossiers actifs, les dossiers en attente, les No-Go critiques et les dossiers archivés. Il présente également un graphique de répartition et une liste de dossiers prioritaires.

Le manager peut valider une décision No-Go avec `VALIDATE_NOGO` ou déclencher un `FORCE_GO`. Le forçage exige une justification d’au moins cinquante mots. Le type de forçage, le nom du manager, la justification et la date sont conservés dans le modèle de score et dans la piste d’audit.

Le module de validation gère des validateurs identifiés par les rôles DO, DDA, DGA et PDG. Les statuts manipulés sont `PENDING`, `APPROVED` et `REJECTED`. Des jetons de validation sont gérés côté `project-service` et les expirations sont contrôlées par une tâche planifiée. Les notifications utilisent le système de courriel configuré avec Mailpit en environnement local.

### 5.6.3 Interfaces à illustrer

- Figure 5.15 — Tableau de bord manager avec indicateurs et dossiers prioritaires.
- Figure 5.16 — Boîte de dialogue de Force-Go avec justification obligatoire.
- Figure 5.17 — Historique des décisions ou suivi des validations hiérarchiques.

Les actions de décision s’appuient notamment sur `POST /api/scoring/{id}/force-go`, `POST /api/scoring/{id}/confirm-nogo`, `POST /api/dossiers/{id}/decision-nogo` et les routes de validation du `project-service` sous le préfixe `/api/validation`.

## 5.7 Module 5 — Audit, tests et déploiement (Epic 5, Sprint 8)

### 5.7.1 Objectif fonctionnel

Le dernier module concerne l’audit final, l’archivage, les tests E2E disponibles dans le frontend et le démarrage de l’environnement avec Docker Compose.

| Sprint | Objectif | Story Points |
|---|---|---:|
| Sprint 8 | Tests E2E, recette fonctionnelle et déploiement | 13 |

### 5.7.2 Rapport d’audit et archivage

`AuditGenerationService` génère un rapport d’audit au format DOCX à partir du modèle `Rapport-Audit-Template.docx`. Il récupère le dossier, la piste d’audit et le score P-Win. Le document contient les informations du dossier, la décision, le détail des cinq axes, le risque rédhibitoire éventuel, les corrections humaines et les données de Force-Go.

La chronologie des actions est injectée dans le tableau prévu par le modèle. Une analyse narrative et des points d’amélioration sont demandés au service IA ; si cet appel échoue, la génération du document reste possible avec la mention « Non disponible ». Le document est stocké dans le bucket MinIO `rapports-audit`. Après publication de l’événement `AUDIT_GENERATED`, le `project-service` enregistre le chemin et archive le dossier.

### 5.7.3 Déploiement Docker Compose

Les services sont connectés au réseau Docker `projectiq-network`. La configuration Compose définit les composants suivants :

| Service | Port hôte exposé | Fonction |
|---|---:|---|
| discovery-server | 8761 | Eureka |
| project-service | 8083 | Dossiers, stockage, extraction P1 et validation |
| analyste-service | 8085 | Analyse approfondie et livrables |
| api-gateway | 8089 | Point d’entrée API |
| ia-service | 8000 | Service FastAPI |
| postgres | variable `POSTGRES_PORT` vers 5432 | Persistance PostgreSQL |
| rabbitmq | 5672 et 15672 | Messagerie et interface de gestion |
| minio | 9000 et 9001 | Stockage objet et console |
| mailpit | 1025 et 8025 | SMTP de test et interface de consultation |

`auth-service` et `admin-service` sont démarrés dans le réseau Docker mais ne publient pas de port hôte dans le fichier Compose actuel. Ils sont atteignables par la Gateway grâce à Eureka. Les routes principales de la passerelle couvrent l’authentification, l’administration, les dossiers, les analyses, le scoring, le matching, l’export et le référentiel.

Le frontend contient également une configuration Cypress et un scénario E2E de flux manager. Le mémoire peut affirmer l’existence de ces tests, mais ne doit pas annoncer un taux de couverture ou une recette complète sans résultats de tests exécutés et archivés.

### 5.7.4 Interfaces à illustrer

- Figure 5.18 — Consultation du rapport ou de la piste d’audit d’un dossier.
- Figure 5.19 — Tableau Eureka montrant les services enregistrés.
- Figure 5.20 — Console RabbitMQ ou console MinIO.

## 5.8 Plan de captures à intégrer

Les captures doivent montrer le résultat fonctionnel et non le code. Il est conseillé de les insérer juste après le sous-titre indiqué ci-dessous, avec une légende uniforme. Les données affichées doivent être des données de démonstration anonymisées.

| Figure | Emplacement dans le mémoire | Capture à insérer | Élément important à rendre visible |
|---|---|---|---|
| 5.1 | Après § 5.3.2 | Écran de connexion | Champs e-mail et mot de passe, message d’erreur ou authentification réussie |
| 5.2 | Après § 5.3.3 | Liste des utilisateurs | Actions de validation, activation ou rejet |
| 5.3 | Après § 5.3.3 | Édition d’un rôle | Regroupement des permissions |
| 5.4 | Après § 5.3.3 | Feature Flags d’un utilisateur | Modules activés ou désactivés |
| 5.5 | Après § 5.3.3 | Journal d’audit | Date, acteur, action et résultat |
| 5.6 | Après § 5.4.2 | Formulaire de dépôt | Fichier TDR, date limite et case « dossier privé » |
| 5.7 | Après § 5.4.3 | Résultats P1 | Valeur, score de confiance, source et bouton de correction |
| 5.8 | Après § 5.4.5 | Configuration IA | Sélection d’un prompt et sauvegarde d’un override |
| 5.9 | Après § 5.5.2 | Analyse approfondie | Avancement SSE et étapes du pipeline |
| 5.10 | Après § 5.5.3 | Écran P-Win | Score global, axes et risques |
| 5.11 | Après § 5.5.4 | Écran de matching | Taux de couverture et matrice de différenciation |
| 5.12 | Après § 5.5.5 | Pack ou rapport No-Go | Livrables disponibles ou justification de la décision |
| 5.13 | Après § 5.6.2 | Dashboard manager | Indicateurs, graphique et dossiers prioritaires |
| 5.14 | Après § 5.6.2 | Fenêtre Force-Go | Justification, type de forçage et validation |
| 5.15 | Après § 5.7.2 | Rapport ou historique d’audit | Chronologie, score et décision finale |
| 5.16 | Après § 5.7.3 | Console technique | Eureka, RabbitMQ ou MinIO ; une seule capture suffit |

> **Emplacement de capture — Figure 5.x**  
> Insérer ici la capture correspondante, centrée et lisible.  
> *Figure 5.x — [reprendre la légende de la ligne correspondante du tableau].*

## 5.9 Conclusion

L’implémentation de ProjectIQ repose sur une architecture distribuée adaptée au traitement progressif des appels d’offres. Elle associe une interface Angular, des services Spring Boot spécialisés, un service IA FastAPI, une messagerie asynchrone et un stockage objet. Le système met l’accent sur la traçabilité : accès, modifications administratives, corrections de champs, décisions, appels IA et génération documentaire sont conservés afin de soutenir l’analyse et la supervision managériale.

Cette version décrit uniquement les fonctionnalités visibles dans les dépôts du projet. Les valeurs de configuration, les ports et les règles métier sont présentés conformément au code actuel, sans ajouter de comportement non implémenté.
