# XXL-Job Admin 初始化

1. 启动 MySQL（compose 内 `mysql` 服务）。
2. 建库并导入官方表结构：

```bash
docker exec -i dgp-mysql mysql -uroot -pdgp_root -e "CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4"
# 从 https://github.com/xuxueli/xxl-job/blob/master/doc/db/tables_xxl_job.sql 下载后：
docker exec -i dgp-mysql mysql -uroot -pdgp_root xxl_job < tables_xxl_job.sql
```

3. 启动 Admin：

```bash
docker compose -f deploy/compose/docker-compose.yml --profile xxl up -d xxl-job-admin
```

默认控制台：`http://127.0.0.1:8090/xxl-job-admin`（账号 admin / 123456，以官方镜像为准）。

4. 执行器：应用名 `dgp-scheduler`，地址自动注册（需 `DGP_XXL_ENABLED=true`）。

5. 推荐先建一个 CRON 任务：Handler=`dgpScanDueAll`，Cron=`0 * * * * ?`（每分钟），无需参数。

按作业精细调度时，用 `GET /api/v1/scheduler/jobs` 返回的 `xxlHandler` + `xxlParam` 逐个注册。
