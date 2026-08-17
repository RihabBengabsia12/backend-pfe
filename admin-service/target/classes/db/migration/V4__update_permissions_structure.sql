-- 1. Ajout de la colonne de classification
ALTER TABLE permission ADD COLUMN IF NOT EXISTS category VARCHAR(100);

-- 2. Mise à jour des catégories avec des noms "Métier"
UPDATE permission SET category = 'Intelligence Documentaire'
WHERE code IN ('FILE_READ', 'AI_ANALYZE', 'METHOD_GEN', 'DATA_EXTRACTION');

UPDATE permission SET category = 'Gouvernance & Verdict'
WHERE code IN ('DECISION_VALIDATE', 'GO_NO_GO_VOTE', 'VIEW_SCORE');

UPDATE permission SET category = 'Administration Système'
WHERE category IS NULL;

-- 3. Mise à jour des libellés (Label) pour un rendu 100% Pro
UPDATE permission SET label = 'Extraction automatique des données' WHERE code = 'FILE_READ';
UPDATE permission SET label = 'Analyse de conformité IA' WHERE code = 'AI_ANALYZE';
UPDATE permission SET label = 'Rédaction assistée de la méthodologie' WHERE code = 'METHOD_GEN';
UPDATE permission SET label = 'Validation du verdict Go/No-Go' WHERE code = 'DECISION_VALIDATE';
UPDATE permission SET label = 'Suivi du journal d''audit' WHERE code = 'AUDIT_VIEW';
UPDATE permission SET label = 'Configuration des accès d''équipe' WHERE code = 'ROLE_MANAGE';

-- 4. Sécurité : on rend la catégorie obligatoire
ALTER TABLE permission ALTER COLUMN category SET NOT NULL;