CREATE DATABASE IF NOT EXISTS walletdb;
USE walletdb;

CREATE TABLE IF NOT EXISTS wallet_account (
  id         BIGINT        NOT NULL PRIMARY KEY,
  owner      VARCHAR(50)   NOT NULL,
  balance    DECIMAL(18,2) NOT NULL,
  version    BIGINT        NOT NULL DEFAULT 0,
  updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- the two "hot" rows every simulated request fights over
INSERT INTO wallet_account (id, owner, balance) VALUES
  (1, 'hot-user-1', 1000000000.00),
  (2, 'hot-user-2', 1000000000.00)
ON DUPLICATE KEY UPDATE balance = VALUES(balance), version = 0;
