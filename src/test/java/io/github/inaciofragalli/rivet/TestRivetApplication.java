package io.github.inaciofragalli.rivet;

import org.springframework.boot.SpringApplication;

public class TestRivetApplication {

    public static void main(String[] args) {
        SpringApplication.from(RivetApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
