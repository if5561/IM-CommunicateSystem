package com.lld.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@EnableRabbit
@SpringBootApplication
@MapperScan("com.lld.message.dao.mapper")
public class ImMessageStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImMessageStoreApplication.class, args);
    }

}
