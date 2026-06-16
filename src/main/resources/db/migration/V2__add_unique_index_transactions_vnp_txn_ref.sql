CREATE UNIQUE INDEX uk_transactions_vnp_txn_ref
ON transactions (vnp_txn_ref)
WHERE vnp_txn_ref IS NOT NULL;
