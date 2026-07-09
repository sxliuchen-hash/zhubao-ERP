-- M3.127 Link gateway reading consumption to a real business command.
-- Adds the ledger code produced when a WEIGH_HANDOVER / SCAN_TO_REPORT consumption drives a
-- production report through BusinessOperationService. Blank when the consumption is a pure
-- bridge record (no production posting context supplied).

ALTER TABLE hw_gateway_reading_consumption
    ADD COLUMN ledger_code VARCHAR(80) NULL AFTER note;
