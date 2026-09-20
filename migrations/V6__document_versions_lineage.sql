-- F06 document generation: versions record which job produced them, and a version's content
-- never changes after it is written (domain/version-lineage.md). Corrections are new versions.

ALTER TABLE document_versions
    ADD COLUMN source_job_id uuid REFERENCES jobs (id),
    ADD COLUMN execution_id  uuid REFERENCES ai_executions (id);

CREATE TRIGGER document_versions_immutable
    BEFORE UPDATE ON document_versions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
