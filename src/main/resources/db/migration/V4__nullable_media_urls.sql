-- A video row now exists before transcoding finishes (video_url is set when the DASH manifest is
-- uploaded) and before a thumbnail is generated. Previously placeholder URLs containing "null" were
-- stored instead. Columns are also widened for longer object keys. Existing values are preserved.

ALTER TABLE video
  MODIFY video_url varchar(1024) NULL,
  MODIFY thumbnail_url varchar(1024) NULL;

ALTER TABLE guide MODIFY image_url varchar(1024) NULL;
