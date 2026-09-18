-- Users first (tasks depend on these via user_id)
INSERT INTO users (username, password, role) VALUES
('Ajay', '$2b$10$.8fP4TPs0lF1pAfFR4X.3u0YyIbninbdfP9n0lWKiFe5Q2FVwC3/e', 'USER'),
('Rahul', '$2b$10$KXM3fu.YdxthgmYU86bTReXUU00B5Gt3DCvwi3e.ovVho3lWnP/jG', 'ADMIN');

-- Tasks, owned by Ajay
INSERT INTO tasks (title, description, completed, created_at, user_id) VALUES
('Buy groceries', 'Milk, eggs, bread, butter', false, now(),
 (SELECT id FROM users WHERE username = 'Ajay')),

('Finish Spring Security plan', 'Work through Phase 1 to Phase 10', false, now(),
 (SELECT id FROM users WHERE username = 'Ajay')),

('Clean the apartment', 'Vacuum, dishes, laundry', true, now(),
 (SELECT id FROM users WHERE username = 'Ajay')),

('Read a book', 'Finish current chapter of Clean Code', false, now(),
 (SELECT id FROM users WHERE username = 'Ajay')),

('Book dentist appointment', 'Check availability for next week', false, now(),
 (SELECT id FROM users WHERE username = 'Ajay')),

('Renew car insurance', 'Compare quotes before renewal date', true, now(),
 (SELECT id FROM users WHERE username = 'Ajay'));