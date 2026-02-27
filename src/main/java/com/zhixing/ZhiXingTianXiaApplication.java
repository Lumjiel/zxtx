package com\.zhixing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com\.zhixing.mapper")
@SpringBootApplication
public class ZhiXingTianXiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiXingTianXiaApplication.class, args);
    }

}








