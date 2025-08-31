# 基础镜像
FROM openjdk:17-slim

# 作者
MAINTAINER wyj

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/ai-rag-knowledge:1.0.jar /ai-rag-knowledge:1.0.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /ai-rag-knowledge:1.0.jar $PARAMS"]