DROP TABLE IF EXISTS article_reviews;
DROP TABLE IF EXISTS articles;
DROP TABLE IF EXISTS submission_periods;
DROP TABLE IF EXISTS reviewers;
DROP TABLE IF EXISTS authors;

DROP FUNCTION IF EXISTS register_author;
DROP FUNCTION IF EXISTS add_reviewer;
DROP FUNCTION IF EXISTS remove_reviewer;
DROP FUNCTION IF EXISTS review;
DROP FUNCTION IF EXISTS is_submission_open;
DROP FUNCTION IF EXISTS add_submission_period;
DROP FUNCTION IF EXISTS assign_reviewers;
DROP FUNCTION IF EXISTS update_article_status;
DROP PROCEDURE IF EXISTS submit_article;

DROP TYPE IF EXISTS review_decision;
DROP TYPE IF EXISTS article_type;
DROP TYPE IF EXISTS article_status;

----------------------------------------------------------------------------------------------------------------
-- TYPES
----------------------------------------------------------------------------------------------------------------

CREATE TYPE article_status AS ENUM(
    'submitted',
    'under_review',
    'accepted',
    'rejected'
);

CREATE TYPE article_type AS ENUM(
    'short_article',
    'full_article',
    'poster'
);

CREATE TYPE review_decision AS ENUM(
    'accepted',
    'rejected'
);

----------------------------------------------------------------------------------------------------------------
-- TABLES
----------------------------------------------------------------------------------------------------------------

CREATE TABLE authors(
    author_id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    affiliation VARCHAR(100),
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(100)
);

CREATE TABLE reviewers(
    reviewer_id SERIAL PRIMARY KEY,
    id_number VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(100),
    research_area VARCHAR(100),
	active BOOL NOT NULL DEFAULT TRUE
);

CREATE TABLE submission_periods(
    year INT PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL
);

CREATE TABLE articles(
    article_id SERIAL PRIMARY KEY,
    year INT NOT NULL REFERENCES submission_periods(year),
    author INT NOT NULL REFERENCES authors(author_id),
    type article_type NOT NULL,
    title VARCHAR(100) NOT NULL,
    keywords VARCHAR(100),
    text TEXT NOT NULL,
    status article_status NOT NULL DEFAULT 'submitted'
);

CREATE TABLE article_reviews(
    article INT NOT NULL REFERENCES articles(article_id),
    reviewer INT NOT NULL REFERENCES reviewers(reviewer_id),
    decision review_decision,
    comment VARCHAR(100),

    PRIMARY KEY(article, reviewer)
);

----------------------------------------------------------------------------------------------------------------
-- FUNCTIONS / PROCEDURES
----------------------------------------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION register_author(a_first_name VARCHAR(100), a_last_name VARCHAR(100), a_affiliation VARCHAR(100), a_email VARCHAR(100), a_phone_number VARCHAR(100))
RETURNS BOOL
LANGUAGE plpgsql
AS 
$$
BEGIN
	IF EXISTS (SELECT 1 FROM authors WHERE email = a_email) THEN
		RETURN FALSE;
	END IF;

	INSERT INTO authors(first_name, last_name, affiliation, email, phone_number) VALUES (a_first_name, a_last_name, a_affiliation, a_email, a_phone_number);
	RETURN TRUE;
END;
$$;


CREATE OR REPLACE FUNCTION add_reviewer(r_id_number VARCHAR(100), r_full_name VARCHAR(100), r_phone_number VARCHAR(100), r_research_area VARCHAR(100))
RETURNS BOOL
LANGUAGE plpgsql
AS 
$$
BEGIN
	IF EXISTS (SELECT 1 FROM reviewers WHERE id_number = r_id_number) THEN
		RETURN FALSE;
	END IF;

	INSERT INTO reviewers(id_number, full_name, phone_number, research_area) VALUES (r_id_number, r_full_name, r_phone_number, r_research_area);
	RETURN TRUE;
END;
$$;


CREATE OR REPLACE FUNCTION remove_reviewer(target_reviewer INT)
RETURNS INT
LANGUAGE plpgsql
AS
$$
BEGIN
	IF EXISTS (SELECT 1 FROM reviewers WHERE reviewer_id = target_reviewer AND active = TRUE) THEN
		UPDATE reviewers SET ACTIVE = FALSE WHERE reviewer_id = target_reviewer;
		RETURN 0;
	END IF;
	
	IF EXISTS (SELECT 1 FROM reviewers WHERE reviewer_id = target_reviewer) THEN
		RETURN 1;
	END IF;
	
	RETURN 2;
END;
$$;


CREATE OR REPLACE FUNCTION review(r_reviewer INT, r_article INT, r_decision review_decision, r_comment VARCHAR(100))
RETURNS BOOL
LANGUAGE plpgsql
AS
$$
BEGIN
	UPDATE article_reviews SET decision = r_decision, comment = r_comment WHERE article = r_article AND reviewer = r_reviewer;

	IF NOT FOUND THEN
		RETURN FALSE;
	END IF;
	
	RETURN TRUE;
END;
$$;


CREATE OR REPLACE PROCEDURE submit_article(today DATE, a_author INT, a_type article_type, a_title VARCHAR(100), a_keywords VARCHAR(100), a_text TEXT)
LANGUAGE plpgsql
AS
$$
BEGIN
	INSERT INTO articles(year, author, type, title, keywords, text) VALUES (EXTRACT(YEAR FROM today), a_author, a_type, a_title, a_keywords, a_text);
END;
$$;


CREATE OR REPLACE FUNCTION is_submission_open(today DATE) 
RETURNS BOOL
LANGUAGE plpgsql
AS 
$$
DECLARE
    s_start_date DATE;
    s_end_date DATE;
BEGIN
    SELECT start_date, end_date INTO s_start_date, s_end_date
    FROM submission_periods
    WHERE year = EXTRACT(YEAR FROM today);

    IF s_start_date IS NULL OR s_end_date IS NULL THEN
        RETURN FALSE;
    END IF;

    RETURN today BETWEEN s_start_date AND s_end_date;
END;
$$;


CREATE OR REPLACE FUNCTION add_submission_period(s_start_date DATE, s_end_date DATE)
RETURNS INT
LANGUAGE plpgsql
AS
$$
BEGIN
	IF s_start_date >= s_end_date THEN
        RETURN 1;
    END IF;

	IF EXISTS (SELECT 1 FROM submission_periods WHERE year = EXTRACT(YEAR FROM s_start_date)) THEN
		RETURN 2;
	END IF;

	INSERT INTO submission_periods(year, start_date, end_date) VALUES (EXTRACT(YEAR FROM s_start_date), s_start_date, s_end_date);
	RETURN 0;
END;
$$;


CREATE OR REPLACE FUNCTION assign_reviewers(target_article INT, reviewer1 INT, reviewer2 INT)
RETURNS INT
LANGUAGE plpgsql 
AS 
$$
DECLARE
	reviewer_count INT;
BEGIN
   	SELECT COUNT(*) INTO reviewer_count
	FROM article_reviews
	WHERE article = target_article;

	IF reviewer_count = 2 THEN
		RETURN 1;
	END IF;

	IF reviewer1 = reviewer2 THEN
		RETURN 2;	
	END IF;

	IF EXISTS (SELECT 1 FROM article_reviews WHERE article = target_article AND reviewer IN (reviewer1, reviewer2)) THEN
		RETURN 3;
	END IF;

	IF EXISTS (SELECT 1 FROM reviewers WHERE reviewer_id IN (reviewer1, reviewer2) AND active = FALSE) THEN
        RETURN 4;
    END IF;

    INSERT INTO article_reviews(article, reviewer) VALUES (target_article, reviewer1);
    INSERT INTO article_reviews(article, reviewer) VALUES (target_article, reviewer2);

    UPDATE articles SET status = 'under_review' WHERE article_id = target_article;
	RETURN 0;	
END;
$$;

----------------------------------------------------------------------------------------------------------------
-- TRIGGERS
----------------------------------------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_article_status() 
RETURNS TRIGGER
LANGUAGE plpgsql
AS 
$$
DECLARE
	target_article INT := NEW.article;
    review_count INT;
    accepted_count INT;
	pending_count INT;
BEGIN
    SELECT COUNT(*) INTO review_count
    FROM article_reviews
    WHERE article = target_article;

	SELECT COUNT(*) INTO pending_count
    FROM article_reviews
    WHERE article = target_article
	AND decision IS NULL;
	
    SELECT COUNT(*) INTO accepted_count
    FROM article_reviews
    WHERE article = target_article
	AND decision = 'accepted';

    IF review_count = 2 AND pending_count = 0 THEN
		IF accepted_count = 2 THEN
			UPDATE articles SET status = 'accepted' WHERE article_id = target_article;
		ELSE
			UPDATE articles SET status = 'rejected' WHERE article_id = target_article;
		END IF;
	END IF;
	
	RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_article_review_status
AFTER UPDATE OF decision ON article_reviews
FOR EACH ROW EXECUTE FUNCTION update_article_status();

----------------------------------------------------------------------------------------------------------------
-- AUTHORS
----------------------------------------------------------------------------------------------------------------

INSERT INTO authors(first_name, last_name, affiliation, email, phone_number) VALUES
('Alice', 'Smith', 'University of Oxford', 'alice.smith@oxford.ac.uk', '+44 1234 567890'),
('Bob', 'Johnson', 'MIT', 'bob.johnson@mit.edu', '+1 617 555 0123'),
('Carol', 'Lee', 'Stanford University', 'carol.lee@stanford.edu', '+1 650 555 9876'),
('David', 'Kim', 'Seoul National University', 'david.kim@snu.ac.kr', '+82 2 880 1234'),
('Eva', 'Martinez', 'University of Barcelona', 'eva.martinez@ub.edu', '+34 93 403 1234'),
('Frank', 'Müller', 'Technical University of Munich', 'frank.mueller@tum.de', '+49 89 289 12345'),
('Grace', 'Chen', 'Tsinghua University', 'grace.chen@tsinghua.edu.cn', '+86 10 6278 1234'),
('Henry', 'Brown', 'University of Toronto', 'henry.brown@utoronto.ca', '+1 416 555 2345');

----------------------------------------------------------------------------------------------------------------
-- REVIEWERS
----------------------------------------------------------------------------------------------------------------

INSERT INTO reviewers(id_number, full_name, phone_number, research_area, active) VALUES
('R001', 'Prof. John Davis', '+1 555 101 2020', 'Machine Learning', TRUE),
('R002', 'Dr. Sarah Wilson', '+44 20 7946 0123', 'Computer Vision', TRUE),
('R003', 'Dr. Michael Thompson', '+1 617 555 3456', 'Natural Language Processing', TRUE),
('R004', 'Prof. Anna Becker', '+49 89 289 6789', 'Robotics', TRUE),
('R005', 'Dr. Chen Wei', '+86 10 6278 5678', 'Cybersecurity', TRUE),
('R006', 'Dr. Laura Rossi', '+39 06 555 1234', 'Data Science', TRUE),
('R007', 'Prof. David Garcia', '+34 91 555 4321', 'Software Engineering', TRUE);

----------------------------------------------------------------------------------------------------------------
-- SUBMISSION PERIODS
----------------------------------------------------------------------------------------------------------------

INSERT INTO submission_periods(year, start_date, end_date) VALUES
(2020, '2020-01-15', '2020-03-15'),
(2021, '2021-01-20', '2021-03-20'),
(2022, '2022-02-01', '2022-04-01'),
(2023, '2023-01-10', '2023-03-10'),
(2024, '2024-02-05', '2024-04-05'),
(2025, '2025-01-25', '2025-03-25');

----------------------------------------------------------------------------------------------------------------
-- ARTICLES
----------------------------------------------------------------------------------------------------------------

INSERT INTO articles(year, author, type, title, keywords, text, status) VALUES
(2020, 1, 'full_article', 'Deep Learning for Image Classification', 'deep learning, image classification', 'This article explores deep learning models...', 'accepted'),
(2020, 2, 'short_article', 'Quantum Computing Basics', 'quantum computing, qubits', 'Introduction to quantum computing...', 'rejected'),
(2021, 3, 'poster', 'NLP for Social Media', 'NLP, social media, sentiment', 'We study NLP techniques applied to social media...', 'accepted'),
(2021, 4, 'full_article', 'Autonomous Navigation in Robotics', 'robotics, autonomous systems', 'This work presents methods for robot navigation...', 'rejected'),
(2022, 5, 'short_article', 'Cybersecurity Threat Detection', 'cybersecurity, threat detection', 'We propose a method for detecting cybersecurity threats...', 'accepted'),
(2022, 6, 'full_article', 'Big Data Analytics in Healthcare', 'big data, healthcare, analytics', 'Analysis of healthcare data using big data techniques...', 'accepted'),
(2023, 7, 'poster', 'Agile Methodologies for Software Teams', 'agile, software engineering', 'Study of agile practices in software teams...', 'rejected'),
(2023, 1, 'full_article', 'Convolutional Neural Networks Optimization', 'CNN, optimization, deep learning', 'Techniques to optimize CNN training...', 'accepted'),
(2024, 2, 'short_article', 'Reinforcement Learning for Game AI', 'reinforcement learning, AI', 'Applying RL to game AI development...', 'accepted'),
(2024, 3, 'full_article', 'Sentiment Analysis on Multilingual Datasets', 'NLP, sentiment analysis, multilingual', 'Evaluating sentiment analysis models across languages...', 'rejected'),
(2025, 4, 'poster', 'SLAM Techniques for Autonomous Vehicles', 'SLAM, autonomous vehicles', 'Simultaneous localization and mapping methods...', 'accepted'),
(2025, 5, 'full_article', 'Blockchain Applications in Finance', 'blockchain, finance, cryptocurrency', 'We discuss blockchain use cases in financial systems...', 'accepted');

----------------------------------------------------------------------------------------------------------------
-- ARTICLE REVIEWS
----------------------------------------------------------------------------------------------------------------

INSERT INTO article_reviews(article, reviewer, decision, comment) VALUES
(1, 1, 'accepted', 'Excellent work on CNN models.'),
(1, 2, 'accepted', 'Well-structured and comprehensive.'),
(2, 3, 'rejected', 'Needs more experimental results.'),
(2, 4, 'rejected', 'Not convincing.'),
(3, 1, 'accepted', 'Good use of social media datasets.'),
(3, 5, 'accepted', 'Clear and concise poster.'),
(4, 2, 'rejected', 'Methodology is unclear.'),
(4, 6, 'rejected', 'Lacks novelty.'),
(5, 5, 'accepted', 'Interesting approach to threat detection.'),
(5, 3, 'accepted', 'Well-written short article.'),
(6, 1, 'accepted', 'Comprehensive data analysis.'),
(6, 2, 'accepted', 'Very informative article.'),
(7, 4, 'rejected', 'Poster lacks depth.'),
(7, 6, 'rejected', 'Too brief.'),
(8, 3, 'accepted', 'Excellent optimization techniques.'),
(8, 7, 'accepted', 'Clear results and explanation.'),
(9, 2, 'accepted', 'RL methods are well applied.'),
(9, 5, 'accepted', 'Good examples of reinforcement learning.'),
(10, 1, 'rejected', 'Multilingual analysis is weak.'),
(10, 6, 'rejected', 'Needs more datasets.'),
(11, 3, 'accepted', 'SLAM methods are explained clearly.'),
(11, 4, 'accepted', 'Useful for autonomous vehicle research.'),
(12, 5, 'accepted', 'Blockchain applications are practical.'),
(12, 7, 'accepted', 'Good explanation of financial use cases.');

























