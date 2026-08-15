package com.example.clustermanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MQCluster 应用主入口 —— Spring Boot 启动类。
 *
 * <p>本类是整个 MQCluster 后端应用的启动入口，负责初始化 Spring 应用上下文、
 * 自动配置组件扫描、定时任务支持和配置属性绑定。应用采用六边形架构（端口与适配器），
 * 通过此入口统一加载所有层的组件。
 *
 * <p><b>{@link SpringBootApplication}</b>：启用 Spring Boot 自动配置、组件扫描
 * （默认扫描本类所在包 {@code com.example.clustermanager} 及其子包）和
 * Spring Boot 配置。所有 {@code @Component}、{@code @Service}、{@code @RestController}
 * 等注解的类在此范围内自动注册为 Bean。
 *
 * <p><b>{@link EnableScheduling}</b>：启用 Spring 的定时任务调度支持，使
 * {@code @Scheduled} 注解生效。本应用中主要用于 {@code ClusterTelemetryPushService}，
 * 它按固定周期（默认 5000ms，由 {@code cluster.stream.publish-interval-ms} 配置）
 * 通过 STOMP 向前端推送集群遥测数据（metrics + logs）。
 *
 * <p><b>{@link ConfigurationPropertiesScan}</b>：扫描带有
 * {@code @ConfigurationProperties} 注解的类并自动注册为 Bean。扫描范围覆盖
 * {@code com.example.clustermanager} 及其子包，包括：
 * <ul>
 *   <li>{@code infrastructure.pseudo} —— 伪集群配置（{@code cluster.pseudo.*}）</li>
 *   <li>{@code infrastructure.rocketmq} —— 真实 RocketMQ 集群配置（{@code cluster.rocketmq.*}）</li>
 *   <li>{@code api.config} —— CORS、WebSocket 流推送等配置</li>
 * </ul>
 * 这使得各层的 {@code @ConfigurationProperties} record 无需额外 {@code @Bean}
 * 声明即可自动注入。
 *
 * <p><b>启动方式</b>：
 * <ul>
 *   <li>开发环境：{@code mvnw.cmd spring-boot:run}（http://localhost:8080）</li>
 *   <li>生产打包：{@code mvnw.cmd clean package} 生成 fat JAR 后
 *       {@code java -jar target/mqcluster-0.1.0-SNAPSHOT.jar}</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class ClusterManagerApplication {

    /**
     * 应用主入口方法 —— 启动 Spring Boot 应用。
     *
     * @param args 命令行参数，透传给 {@link SpringApplication#run}
     */
    public static void main(String[] args) {
        SpringApplication.run(ClusterManagerApplication.class, args);
    }

}
