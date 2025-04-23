CREATE INDEX idx_test_suite_workflow_type ON test_suite(workflow_run_id, test_type_id);
CREATE INDEX idx_test_case_name_class ON test_case(name, class_name);
CREATE INDEX idx_test_case_status ON test_case(status);