CREATE TABLE USERS(id VARCHAR(64) PRIMARY KEY, name VARCHAR(1023) NOT NULL, password VARCHAR(32) NOT NULL);
CREATE UNIQUE INDEX UNIQUE_INDEX_USER_NAME ON USERS (name);
