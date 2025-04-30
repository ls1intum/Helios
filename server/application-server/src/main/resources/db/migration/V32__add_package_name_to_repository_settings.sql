-- Add packageName column to repository_settings table
ALTER TABLE repository_settings
ADD COLUMN package_name VARCHAR(255);