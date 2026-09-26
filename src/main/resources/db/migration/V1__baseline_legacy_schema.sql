-- Baseline: the schema that Hibernate `ddl-auto=update` produced for the original entity model
-- (captured from MySQL 8.4). Existing databases are baselined at this version and skip this script
-- (spring.flyway.baseline-on-migrate=true); new databases are created from it.

CREATE TABLE category (
  id bigint NOT NULL AUTO_INCREMENT,
  description varchar(255) DEFAULT NULL,
  name varchar(255) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_account (
  id binary(16) NOT NULL,
  email varchar(255) NOT NULL,
  enabled tinyint(1) NOT NULL DEFAULT '1',
  password varchar(255) NOT NULL,
  role enum('ROLE_ADMIN','ROLE_USER') NOT NULL,
  username varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKcastjbvpeeus0r8lbpehiu0e4 (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE video (
  id bigint NOT NULL AUTO_INCREMENT,
  description varchar(255) DEFAULT NULL,
  difficulty_level varchar(255) DEFAULT NULL,
  thumbnail_url varchar(255) NOT NULL,
  title varchar(255) NOT NULL,
  upload_date date NOT NULL,
  video_url varchar(255) NOT NULL,
  views bigint NOT NULL,
  category_id bigint DEFAULT NULL,
  user_id binary(16) NOT NULL,
  PRIMARY KEY (id),
  KEY FKq1uengrrk45ucgcdp7kt7qc4k (category_id),
  KEY FKg8w0qnmcbnnrq5u2mm5569ays (user_id),
  CONSTRAINT FKg8w0qnmcbnnrq5u2mm5569ays FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT FKq1uengrrk45ucgcdp7kt7qc4k FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE video_materials (
  video_id bigint NOT NULL,
  materials varchar(255) DEFAULT NULL,
  KEY FK67vp73bj3ff8w3kejipl9bxa8 (video_id),
  CONSTRAINT FK67vp73bj3ff8w3kejipl9bxa8 FOREIGN KEY (video_id) REFERENCES video (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE guide (
  id bigint NOT NULL AUTO_INCREMENT,
  content text NOT NULL,
  image_url varchar(255) DEFAULT NULL,
  title varchar(255) NOT NULL,
  user_id binary(16) NOT NULL,
  video_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK74oos9t0222pp81mp8m6m0ilv (user_id),
  KEY FKgilv5mxsunlp9jimp0x5rgnli (video_id),
  CONSTRAINT FKgilv5mxsunlp9jimp0x5rgnli FOREIGN KEY (video_id) REFERENCES video (id),
  CONSTRAINT FKop7t96ao61he77qd7g0eyxcty FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE step (
  id bigint NOT NULL AUTO_INCREMENT,
  description varchar(255) DEFAULT NULL,
  step_number int NOT NULL,
  title varchar(255) DEFAULT NULL,
  video_timestamp varchar(255) DEFAULT NULL,
  guide_id bigint DEFAULT NULL,
  PRIMARY KEY (id),
  KEY FKb7h5qlbp8wubgfdnp0899b3pe (guide_id),
  CONSTRAINT FKb7h5qlbp8wubgfdnp0899b3pe FOREIGN KEY (guide_id) REFERENCES guide (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task (
  task_id varchar(255) NOT NULL,
  end_time datetime(6) DEFAULT NULL,
  error_details varchar(255) DEFAULT NULL,
  input_path varchar(255) DEFAULT NULL,
  output_location varchar(255) DEFAULT NULL,
  progress double NOT NULL,
  start_time datetime(6) DEFAULT NULL,
  status tinyint DEFAULT NULL,
  PRIMARY KEY (task_id),
  CONSTRAINT task_chk_1 CHECK ((status between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE editor_pick (
  id bigint NOT NULL AUTO_INCREMENT,
  video_id bigint DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK5lyld2msirmd3fnacxnlrcqqv (video_id),
  CONSTRAINT FKd6lj153hmqfr4tkoao9g0h5in FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
