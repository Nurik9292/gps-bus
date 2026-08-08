ALTER TABLE route_swap_verdicts
    DROP CONSTRAINT chk_route_swap_verdict;

ALTER TABLE route_swap_verdicts
    ADD CONSTRAINT chk_route_swap_verdict CHECK (verdict IN
        ('SWAP_SUSPECTED', 'SWAP_CONFIRMED', 'NO_AXIS_FITS', 'INTRA_FAMILY_MISMATCH',
         'PROVIDER_MISMATCH'));
