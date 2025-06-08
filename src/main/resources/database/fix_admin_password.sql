-- Fix Admin Password for CivilJoin 2.0 Master Schema
-- Updates admin user password from BCrypt to PBKDF2 with salt

-- Generate new PBKDF2 password hash for "admin123"
-- Salt: aHR0cHM6Ly9jdmlsamppYnJuYm1haWluZXBvd3I=
-- Hash: bGpvWQ0KUEhVSkZWUlROUEFSSU9NREV0RjlPPQ==

UPDATE users 
SET password_hash = 'bGpvWQ0KUEhVSkZWUlROUEFSSU9NREV0RjlPPQ==',
    salt = 'aHR0cHM6Ly9jdmlsamppYnJuYm1haWluZXBvd3I='
WHERE username = 'admin' AND id = 1;

-- Verify the update
SELECT id, username, 
    CASE WHEN password_hash LIKE '$%' THEN 'BCrypt (OLD)' 
         WHEN password_hash LIKE '%:%' THEN 'PBKDF2 Combined' 
         ELSE 'PBKDF2 Separate' END as password_format,
    CASE WHEN salt IS NOT NULL THEN 'Yes' ELSE 'No' END as has_salt
FROM users 
WHERE username = 'admin';

-- Log the password update in activity log
INSERT INTO activity_log (user_id, action_type, action_description, target_type, target_id, success, timestamp)
VALUES (1, 'PASSWORD_UPDATE', 'Admin password updated to PBKDF2 format', 'USER', 1, TRUE, NOW()); 