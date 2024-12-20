# 选择基础镜像，例如 OpenJDK
FROM openjdk:17-jdk

# 创建工作目录
WORKDIR /app

# 将项目的 JAR 包复制到镜像中
COPY target/hm-dianping-0.0.1-SNAPSHOT.jar /app/heima.jar

# 暴露应用所需的端口
EXPOSE 8080

# 配置启动命令
ENTRYPOINT ["java", "-jar", "/app/heima.jar"]