# 数据库初始化脚本目录

将 SQL 初始化脚本放在此目录中，MySQL 容器启动时会自动执行。

## 使用方法

1. 将 `.sql` 文件放在此目录
2. 文件会按字母顺序执行
3. 建议使用数字前缀命名，如：
   - `01-schema.sql`
   - `02-data.sql`
   - `03-permissions.sql`

## 注意事项

- 脚本只在首次创建数据库时执行
- 如需重新执行，需要删除 MySQL 数据卷：
  ```bash
  docker-compose -f docker-compose.prod.yaml down -v
  ```
