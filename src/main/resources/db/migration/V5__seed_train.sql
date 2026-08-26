INSERT INTO trains (
    train_no,
    name,
    route_id,
    active
)
SELECT
    '12001',
    'Shatabdi Express Demo',
    id,
    TRUE
FROM routes
WHERE route_code = 'DEL-LKO';