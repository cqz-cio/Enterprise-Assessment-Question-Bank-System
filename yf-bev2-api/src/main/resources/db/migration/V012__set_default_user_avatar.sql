-- Use the current super administrator avatar for every account that does not
-- yet have one. Existing custom avatars are preserved.
SET @default_avatar = 'https://be2.yfhl.net/upload/file/2025/04/23/1914975014591750145.jpeg';

UPDATE `el_sys_user`
SET `avatar` = @default_avatar
WHERE `avatar` IS NULL OR TRIM(`avatar`) = '';

ALTER TABLE `el_sys_user`
    MODIFY COLUMN `avatar` varchar(255) DEFAULT 'https://be2.yfhl.net/upload/file/2025/04/23/1914975014591750145.jpeg' COMMENT '用户头像';
