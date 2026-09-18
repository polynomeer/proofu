-- V1 declared status varchar(20) while its CHECK allows 'REJECTED_BY_VALIDATION' (22 chars),
-- so that status could never be stored. Widen the column; the CHECK is unchanged.

ALTER TABLE ai_executions ALTER COLUMN status TYPE varchar(30);
