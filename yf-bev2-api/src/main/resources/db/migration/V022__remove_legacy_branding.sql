-- Remove upstream product branding without overwriting custom configuration.
UPDATE el_cfg_base SET site_name = '企业人才考核系统'
WHERE site_name LIKE '%云帆%';
UPDATE el_cfg_base SET copy_right = ''
WHERE copy_right LIKE '%云帆%' OR copy_right LIKE '%yfhl.net%';
UPDATE el_cfg_base SET login_logo = '/brand-logo.png'
WHERE login_logo LIKE '%yfhl.net%';
UPDATE el_cfg_base SET back_logo = '/brand-logo.png'
WHERE back_logo LIKE '%yfhl.net%';
UPDATE el_cfg_base SET login_bg = '/login-illustration.svg'
WHERE login_bg LIKE '%yfhl.net%';

-- Only upstream demo avatars are replaced. Keep uploaded/custom avatars.
UPDATE el_sys_user SET avatar = '/default-avatar.jpg'
WHERE avatar LIKE '%yfhl.net%' OR avatar IS NULL OR TRIM(avatar) = '';
ALTER TABLE el_sys_user
    MODIFY COLUMN avatar varchar(255) DEFAULT '/default-avatar.jpg' COMMENT '用户头像';

-- Match known demo records and original values; never rewrite paper snapshots.
UPDATE el_sys_user SET real_name = '系统管理员'
WHERE id = '1000000000000000001' AND real_name = '云帆超管';
UPDATE el_sys_user SET real_name = '示例员工'
WHERE id = '1914226980618383361' AND real_name = '云帆学员';
UPDATE el_exam SET title = '示例考核'
WHERE id = '1915227067167539202' AND title = '云帆演示考试';
UPDATE el_exam SET content = '<p>考核示例，请按实际岗位要求配置后发放。</p>'
WHERE id = '1915227067167539202'
  AND content = '<p>1、本项目为云帆开源版考试系统。</p><p>2、为正常体验，将考试机会设置为不限制次数。</p><p>3、本系统每天自动重置系统数据，请不要上传重要资料。</p>';
UPDATE el_repo SET title = '示例题库'
WHERE id = '1910524655864012801' AND title = '云帆演示题库';
UPDATE el_sys_dic_value SET title = '示例分类'
WHERE id = '1915226614203678722' AND title = '云帆演示';
UPDATE el_sys_depart SET dept_name = '全品轩'
WHERE id = '1441328268501381121' AND dept_name = '云帆互联';
