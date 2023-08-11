CREATE TABLE public.transformation (
                                       id INT PRIMARY KEY AUTO_INCREMENT,
                                       name VARCHAR(255) NOT NULL,
                                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
