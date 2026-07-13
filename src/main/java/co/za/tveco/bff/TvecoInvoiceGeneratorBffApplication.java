package co.za.tveco.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TvecoInvoiceGeneratorBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(TvecoInvoiceGeneratorBffApplication.class, args);
    }
}
