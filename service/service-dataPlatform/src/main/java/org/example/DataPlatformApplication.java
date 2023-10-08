package org.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

//@ComponentScan("org.example") //为了扫描到service-utils
@SpringBootApplication
@EnableDiscoveryClient //注册到注册中心
@MapperScan("org.example.mapper")
public class DataPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataPlatformApplication.class,args);
    }
}