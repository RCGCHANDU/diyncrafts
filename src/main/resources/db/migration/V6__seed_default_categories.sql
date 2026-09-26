-- Default categories (previously a manual script, src/main/java/.../sql_scripts/inserting_categories.sql).
-- Only inserted when a category with the same name does not exist yet, so existing data is untouched.

INSERT INTO category (name, description)
SELECT 'Woodworking', 'Creating functional and decorative items from wood using techniques like carving, joinery, and staining.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Woodworking');

INSERT INTO category (name, description)
SELECT 'Sewing', 'The craft of stitching fabric together to create clothing, accessories, or home decor using needles and threads.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Sewing');

INSERT INTO category (name, description)
SELECT 'Knitting', 'A method of creating fabric by interlacing loops of yarn with knitting needles to make garments or textiles.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Knitting');

INSERT INTO category (name, description)
SELECT 'Gardening', 'Growing and maintaining plants, flowers, vegetables, and trees in outdoor or indoor cultivated spaces.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Gardening');

INSERT INTO category (name, description)
SELECT 'Home Improvement', 'Enhancing a home''s functionality, aesthetics, or value through renovations, repairs, and upgrades.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Home Improvement');

INSERT INTO category (name, description)
SELECT 'Upcycling', 'Transforming abandoned materials or old objects into new, higher-quality items through creative design and repurposing.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Upcycling');

INSERT INTO category (name, description)
SELECT 'Cooking', 'The process of preparing and combining ingredients to create delicious, nutritious, and visually appealing meals.'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Cooking');
