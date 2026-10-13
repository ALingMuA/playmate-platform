-- 20261013_game_default_icons.sql
-- 为初始内置游戏配置默认封面图（仅在原有封面为空时设置，保护管理员自定义修改）

UPDATE game
SET game_icon_url = '/images/games/wzry.jpg'
WHERE game_name = '王者荣耀' AND (game_icon_url IS NULL OR game_icon_url = '');

UPDATE game
SET game_icon_url = '/images/games/lol.jpg'
WHERE game_name = '英雄联盟' AND (game_icon_url IS NULL OR game_icon_url = '');

UPDATE game
SET game_icon_url = '/images/games/hpjy.jpg'
WHERE game_name = '和平精英' AND (game_icon_url IS NULL OR game_icon_url = '');
