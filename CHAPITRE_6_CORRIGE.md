# Chapitre 6 — Tests et validation

## 6.1 Introduction

Ce chapitre présente la démarche de validation de ProjectIQ. Elle vise à vérifier le démarrage des composants, le fonctionnement des parcours principaux et la cohérence des échanges entre les services. La validation couvre également les mécanismes sensibles : authentification, stockage des documents, anonymisation DLP, communication asynchrone, génération documentaire et traçabilité des appels IA.

Pour rester rigoureux, le chapitre distingue les tests automatisés réellement présents dans les dépôts, la recette fonctionnelle manuelle à exécuter, et les contrôles techniques qui doivent être prouvés par des captures, journaux ou réponses API. Aucun taux de précision IA, coût, temps de réponse ou pourcentage de réussite n'est déclaré sans mesure archivée.

## 6.2 Stratégie de validation

| Niveau | Objectif | Périmètre |
|---|---|---|
| Tests de démarrage | Vérifier le chargement des applications Spring Boot. | Les six applications Spring Boot |
| Test E2E | Vérifier un parcours utilisateur depuis le navigateur. | Connexion et dashboard Manager |
| Recette manuelle | Vérifier les User Stories par rôle. | Administration, analyste et manager |
| Intégration | Vérifier les échanges entre composants. | Gateway, RabbitMQ, MinIO, PostgreSQL, Mailpit et IA |
| Contrôles techniques | Vérifier les garde-fous implémentés. | JWT, BCrypt, DLP, upload, ré-extraction et audit IA |

La recette doit être réalisée dans l'environnement Docker Compose, avec les services visibles dans Eureka et l'application Angular accessible par la Gateway.

## 6.3 Tests automatisés présents

### 6.3.1 Tests Spring Boot

Chaque application Spring Boot possède un test JUnit annoté avec @SpringBootTest. Il vérifie uniquement que le contexte de l'application démarre correctement.

| Application | Classe de test | Vérification |
|---|---|---|
| auth-service | AuthServiceApplicationTests | Chargement du contexte d'authentification |
| admin-service | AdminServiceApplicationTests | Chargement du contexte d'administration |
| project-service | ProjectServiceApplicationTests | Chargement du contexte de gestion des dossiers |
| analyste-service | AnalysteServiceApplicationTests | Chargement du contexte d'analyse |
| api-gateway | ApiGatewayApplicationTests | Chargement de la Gateway |
| discovery-server | DiscoveryServerApplicationTests | Chargement du serveur Eureka |

Ces tests sont nécessaires mais limités : ils ne valident pas les règles métier, les contrôleurs REST, la sécurité ou les communications avec MinIO, RabbitMQ et Anthropic. Ils constituent donc un contrôle de démarrage et non une validation fonctionnelle complète.

### 6.3.2 Test E2E Cypress

Le frontend contient une configuration Cypress et un scénario intitulé manager-flow.cy.ts. Il ouvre la page de connexion, saisit les identifiants d'un manager, déclenche la connexion, vérifie que l'URL contient /manager et contrôle la présence du titre « Tableau de bord ».

| Étape | Résultat attendu |
|---|---|
| Ouverture de /auth/login | La page de connexion est affichée. |
| Saisie et soumission | Le formulaire de connexion est exécuté. |
| Redirection | L'URL contient /manager. |
| Affichage | Le titre « Tableau de bord » est visible. |

> **[Capture 6.1 — Exécution du test Cypress]**  
> Insérer ici une capture de l'interface Cypress ou du terminal montrant l'exécution réussie de manager-flow.cy.ts.

## 6.4 Recette fonctionnelle manuelle

La recette manuelle couvre les cinq Epics. Pour chaque scénario, le résultat doit être renseigné après exécution et accompagné d'une preuve : capture de l'interface, réponse API, journal applicatif, objet MinIO, message RabbitMQ ou courriel Mailpit.

### 6.4.1 Sécurité et administration

| ID | Scénario | Résultat attendu | Preuve |
|---|---|---|---|
| RF-01 | Inscription | Création du compte à l'état PENDING. | Capture et enregistrement du compte. |
| RF-02 | Connexion valide | Réception des jetons et accès à l'espace autorisé. | Dashboard et réponse réseau. |
| RF-03 | Mot de passe erroné | Refus de connexion et événement d'accès. | Réponse HTTP et access_event. |
| RF-04 | Cinq échecs consécutifs | État du compte positionné à LOCKED. | Compteur et état du compte. |
| RF-05 | Validation et activation | Changement d'état et affectation du rôle. | Capture et courriel Mailpit si configuré. |
| RF-06 | Rôles et permissions | Mise à jour et création d'un audit métier. | Écran et data_event. |
| RF-07 | Feature Flags | Modules activés ou désactivés pour l'utilisateur. | Écran et réponse API. |

### 6.4.2 Dépôt, extraction et validation

| ID | Scénario | Résultat attendu | Preuve |
|---|---|---|---|
| RF-08 | Dépôt PDF ou DOCX valide | Dossier créé à l'état UPLOADED et document stocké dans MinIO. | Écran de dépôt et objet MinIO. |
| RF-09 | Fichier non accepté | Rejet des formats autres que PDF, DOCX ou DOC. | Message d'erreur. |
| RF-10 | Fichier supérieur à 100 Mo | Rejet du téléversement. | Réponse d'erreur. |
| RF-11 | Lancement P1 | Passage à PARSING_INITIAL puis traitement de l'analyse. | État et journaux. |
| RF-12 | Consultation P1 | Valeurs, confiance et sources affichées. | Capture d'indexation. |
| RF-13 | Ré-extraction | Mise à jour du champ ciblé, refus au-delà de deux essais. | Réponse API avant/après. |
| RF-14 | Validation P1 | Corrections sauvegardées et dossier INDEXED. | Capture et métadonnées. |
| RF-15 | Dossier privé | Encapsulation DLP avant appel IA. | Journal DLP anonymisé. |
| RF-16 | Prompt Override | Mise à jour d'un prompt ou override de dossier. | Écran de configuration. |

La validation P1 ne bloque pas actuellement l'indexation lorsque le pays, le budget ou les hommes-mois sont absents : le système écrit un avertissement dans les journaux. Ce comportement doit être pris en compte pendant la recette.

### 6.4.3 Analyse, livrables et supervision

| ID | Scénario | Résultat attendu | Preuve |
|---|---|---|---|
| RF-17 | Événement DOSSIER_INDEXED | Démarrage de l'analyse approfondie. | Log RabbitMQ et analyste-service. |
| RF-18 | Extraction P2 et risques | Données P2, niveau et justification des risques disponibles. | Écran P2 et risques. |
| RF-19 | Calcul P-Win | Score, axes et décision automatique enregistrés. | Écran de scoring. |
| RF-20 | NO_GO ou MANUAL | Rapport No-Go généré et pipeline arrêté. | Rapport ou objet MinIO. |
| RF-21 | Matching | Taux de couverture et matrice disponibles. | Écran matching. |
| RF-22 | Livrables et pack ZIP | Documents générés et fichiers disponibles au téléchargement. | MinIO et écran de téléchargement. |
| RF-23 | Dashboard Manager | Indicateurs et dossiers prioritaires affichés. | Capture du dashboard. |
| RF-24 | Validation No-Go | État NO_GO_CONFIRMED et audit de décision. | Historique et statut. |
| RF-25 | Force-Go | Justification d'au moins 50 mots requise et auditée. | Capture et piste d'audit. |
| RF-26 | Archivage | Rapport d'audit généré puis dossier ARCHIVED. | Rapport MinIO et statut. |

> **[Capture 6.2 — Tableau de recette exécuté]**  
> Insérer ici une capture d'un tableau réellement renseigné avec la date, le testeur, le résultat et une référence vers la preuve.

## 6.5 Vérifications techniques

### 6.5.1 Authentification et sécurité

L'authentification repose sur des JWT signés avec HS512. Les mots de passe sont hachés avec BCrypt. La durée configurée de l'Access Token est de 15 minutes. Le Refresh Token est conservé sous forme d'empreinte SHA-256 et il peut être révoqué lors de la déconnexion.

L'intercepteur Angular détecte les réponses 401, tente le renouvellement du jeton et rejoue la requête si le refresh réussit. Sinon, il vide la session et redirige vers la page de connexion.

La Gateway assure le routage et le CORS. Elle ne contient pas de filtre JWT dédié. La validation du jeton est effectuée dans les services qui implémentent un filtre de sécurité. Les droits doivent être testés route par route : par exemple, la gestion des utilisateurs dans admin-service requiert l'autorité ADMIN, tandis que certaines routes sont publiques dans la configuration actuelle.

| Vérification | Attendu |
|---|---|
| Jeton altéré | Rejet par le service validant le JWT. |
| Jeton expiré | Tentative de refresh par l'intercepteur Angular. |
| Requête non authentifiée sur route protégée | Refus ou redirection. |
| Gestion des utilisateurs sans autorité ADMIN | Refus d'accès. |
| Mot de passe en base | Empreinte BCrypt, jamais mot de passe brut. |

### 6.5.2 Documents, DLP et IA

Le project-service accepte les fichiers PDF, DOCX et DOC, avec une taille maximale de 100 Mo. PDFBox extrait le texte des PDF et Apache POI celui des documents Word.

Pour un dossier privé, le DLP remplace les mots actifs du dictionnaire par des codes avant l'appel IA. Les codes sont régénérés quotidiennement. Les valeurs retournées peuvent être décapsulées avant utilisation.

Les journaux IA enregistrent, lorsque ces données sont disponibles, les tokens d'entrée et de sortie, les tokens de cache, le temps de traitement et le coût estimé. Ces métriques servent à analyser des exécutions réelles ; elles ne constituent pas à elles seules une mesure de précision ou de performance.

| Vérification | Attendu |
|---|---|
| PDF ou DOCX valide | Stockage MinIO et extraction du texte. |
| JPG, fichier vide ou fichier > 100 Mo | Rejet avant création du dossier. |
| Dossier privé contenant un mot sensible | Code DLP présent avant l'appel IA. |
| Ré-extraction répétée | Refus après deux tentatives pour un champ. |
| Appel IA | Journal des métriques lorsque disponible. |

> **[Capture 6.3 — DLP ou journaux IA]**  
> Insérer ici une capture anonymisée des journaux IA ou d'une preuve d'encapsulation DLP.

### 6.5.3 Intégration inter-services

Les flux principaux s'appuient sur MinIO, RabbitMQ, Feign, Eureka et l'API Gateway. Les événements configurés comprennent dossier.indexed, dossier.submitted, scoring.completed, matching.completed, apo.generated et audit.generated.

| Flux | Vérification attendue |
|---|---|
| project-service vers MinIO | Présence du TDR et du texte extrait. |
| project-service vers ia-service | Réponse P1 et métadonnées enregistrées. |
| project-service vers RabbitMQ | Publication de dossier.indexed. |
| RabbitMQ vers analyste-service | Démarrage du pipeline approfondi. |
| analyste-service vers project-service | Récupération du dossier et du texte via Feign. |
| analyste-service vers MinIO | Livrables, rapport ou archive disponibles. |
| auth-service vers admin-service | Synchronisation de l'utilisateur par RabbitMQ. |
| Mailpit | Courriels consultables dans l'environnement local. |

> **[Capture 6.4 — Vérification d'intégration]**  
> Insérer ici une capture de RabbitMQ, MinIO ou Eureka montrant un flux exécuté pendant la recette.

## 6.6 Validation de l'IA et limites

Le service IA utilise Claude Haiku 4.5 pour les extractions, l'analyse des risques, le matching et la génération de contenus. Lorsqu'une réponse contient du texte autour du JSON attendu, le client tente d'isoler le bloc JSON. En cas d'échec d'appel, il effectue une tentative initiale et jusqu'à deux nouvelles tentatives avec délai progressif.

La précision de l'IA ne peut pas être affirmée sans jeu de documents de référence et sans annotation manuelle des résultats attendus. Le projet réduit ce risque par les scores de confiance, les sources extraites, les corrections humaines, la validation des risques, la limite de ré-extraction, les journaux IA et l'arrêt du pipeline en cas de décision NO_GO ou MANUAL.

Une validation quantitative future devra comparer les valeurs IA à une vérité terrain annotée, puis calculer les résultats par champ, par type de document et par langue. Les mesures de temps, coût et cache doivent également provenir des journaux d'exécutions réelles.

## 6.7 Bilan et conclusion

Les dépôts contiennent six tests de démarrage Spring Boot et un scénario Cypress pour le parcours Manager. Ils confirment l'existence d'une base de test, mais ne remplacent pas une couverture fonctionnelle complète. La recette proposée couvre les parcours principaux des cinq Epics et précise les preuves à collecter.

Les mécanismes techniques essentiels sont implémentés : JWT HS512, BCrypt, Refresh Token haché, DLP, contrôle des fichiers, limite de ré-extraction, messagerie RabbitMQ, stockage MinIO, génération documentaire et audit IA. Les améliorations prioritaires concernent les tests unitaires métier, les tests d'intégration automatisés, les tests de sécurité, les tests de charge et une campagne de validation IA avec jeu de données annoté.

Cette formulation est adaptée à une soutenance PFE : elle met en valeur les éléments effectivement implémentés, sans déclarer de résultats qui ne seraient pas accompagnés de preuves.
