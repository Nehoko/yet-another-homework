package ge.imikhailov.omno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class OmnoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmnoApplication.class, args);
    }

}
