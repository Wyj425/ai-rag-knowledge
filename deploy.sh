#!/bin/bash
set -e  # 出错时立即退出

# 进入项目根目录
cd "$(dirname "$0")"

echo ">>> 执行 mvn clean install"
mvn clean install

# 进入 app 模块执行 build.sh
cd wyj-dev-tech-app
chmod +x build.sh
echo ">>> 执行 ./build.sh"
./build.sh

# 回到 docker-compose 文件所在目录（根据你的目录结构调整）
cd ../docs/tag/v1.0


echo ">>> 启动环境容器"
docker-compose -f docker-compose-environment-aliyun.yml up -d
echo ">>> 启动项目容器"
docker-compose -f docker-compose-app-v1.0.yml up -d

echo "✅ 部署完成"
