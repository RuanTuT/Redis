# Docker 

### volume

​	•	**命名卷挂载**：适用于容器间共享数据和持久化数据，不依赖宿主机路径，由 Docker 管理。用于容器间共享数据时，数据不会丢失。

​	•	**宿主机路径挂载**：适用于将宿主机的文件或目录直接挂载到容器中，适合操作宿主机上的文件，但不提供跨容器的持久化数据功能。

# Docker compose

一个前后端项目的docker-compose.yml文件配置如下：

* docker-compose.yml

```java
version: '3' # 指定docker-compose版本
services:
  nginx:    # 服务名称  用户自定义
    image: nginx:latest   # 镜像nginx的最新版本
    ports:
      - 80:80     # 暴露端口（宿主:容器），把容器80端口映射到宿主机的80端口
    volumes:      # 挂载（宿主：容器）
      - /root/nginx/html:/usr/share/nginx/html #这里会映射覆盖容器内的目录，然后两个目录会同步
      - /root/nginx/nginx.conf:/etc/nginx/nginx.conf
    privileged: true  # 这个必须要，解决nginx的文件调用的权限问题
  mysql:
    image: mysql:5.7.19
    ports:
      - 3306:3306
    environment:    # 指定 root 用户的密码
      - MYSQL_ROOT_PASSWORD=12345
  redis:
    image: redis:latest
  vue-springboot-blog:  #服务名称
    image: vueblog:latest  #指明构建的镜像的名称
    build: .    # 表示以当前目录下的Dockerfile开始构建镜像
    ports:
      - 8085:8085
    depends_on:   # 依赖于mysql、redis，其实可以不填，默认已经表示可以
      - mysql
      - redis
```

后端项目的 application.yml 或 application-pro.yml 应根据 docker-compose 中服务的名称来配置数据库和 Redis 的地址。

* **application-pro.yml**

```java
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/your_database_name?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: 12345

  redis:
    host: redis
    port: 6379

server:
  port: 8085
```

**同时nginx.conf文件中root路径应该为/usr/share/nginx/html 而不是/root/nginx/html。**